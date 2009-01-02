/*
 *  Copyright 2008 Brian S O'Neill
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.dirmi.io;

import java.io.IOException;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.cojen.util.IntHashMap;

import org.cojen.dirmi.util.Random;

/**
 * Implementation shared by {@link StreamConnectorBroker} and {@link StreamBrokerAcceptor}.
 *
 * @author Brian S O'Neill
 */
abstract class AbstractStreamBroker implements Broker<StreamChannel> {
    static final int DEFAULT_PING_CHECK_MILLIS = 10000;

    static final byte OP_OPEN = 1;
    static final byte OP_OPENED = 2;
    static final byte OP_PING = 3;
    static final byte OP_PONG = 4;
    static final byte OP_CHANNEL_CONNECT = 5;
    static final byte OP_CHANNEL_CONNECTED = 6;
    static final byte OP_CHANNEL_CONNECTED_DIRECT = 7;
    static final byte OP_CHANNEL_CLOSE = 8;

    final Executor mExecutor;

    // FIXME: StreamChannel close/disconnect needs to unregister from map
    private final IntHashMap<StreamChannel> mChannelMap;

    private final LinkedBlockingQueue<AcceptListener<StreamChannel>> mListenerQueue;

    private final ReadWriteLock mCloseLock;
    private boolean mClosed;
    volatile String mClosedReason;

    private final ScheduledFuture<?> mPingCheckTask;

    volatile boolean mPinged;

    {
        mChannelMap = new IntHashMap<StreamChannel>();
        mListenerQueue = new LinkedBlockingQueue<AcceptListener<StreamChannel>>();
        mCloseLock = new ReentrantReadWriteLock(true);
    }

    AbstractStreamBroker(Executor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("Executor is null");
        }
        mExecutor = executor;
        mPingCheckTask = null;
    }

    AbstractStreamBroker(ScheduledExecutorService executor, boolean doPingChecks)
        throws IOException
    {
        if (executor == null) {
            throw new IllegalArgumentException("Executor is null");
        }
        mExecutor = executor;

        if (!doPingChecks) {
            mPingCheckTask = null;
        } else {
            try {
                // Start ping check task.
                long checkRate = DEFAULT_PING_CHECK_MILLIS;
                PingCheckTask checkTask = new PingCheckTask(this);
                mPingCheckTask = ((ScheduledExecutorService) executor).scheduleAtFixedRate
                    (checkTask, checkRate, checkRate, TimeUnit.MILLISECONDS);
                checkTask.setFuture(mPingCheckTask);
            } catch (RejectedExecutionException e) {
                try {
                    close();
                } catch (IOException e2) {
                    // Ignore.
                }
                IOException io = new IOException("Unable to start ping task");
                io.initCause(e);
                throw io;
            }
        }
    }

    public void accept(final AcceptListener<StreamChannel> listener) {
        try {
            Lock lock = closeLock();
            try {
                mListenerQueue.add(listener);
            } finally {
                lock.unlock();
            }
        } catch (final IOException e) {
            try {
                mExecutor.execute(new Runnable() {
                    public void run() {
                        listener.failed(e);
                    }
                });
            } catch (RejectedExecutionException e2) {
                listener.failed(e);
            }
        }
    }

    void accepted(int channelId, StreamChannel channel) {
        register(channelId, channel);

        AcceptListener<StreamChannel> listener = pollListener();
        if (listener != null) {
            listener.established(channel);
        } else {
            // Not accepted in time, so close it.
            unregisterAndDisconnect(channelId, channel);
        }
    }

    AcceptListener<StreamChannel> pollListener() {
        try {
            return mListenerQueue.poll(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return null;
        }
    }

    int reserveChannelId() {
        int channelId;
        synchronized (mChannelMap) {
            do {
                channelId = Random.randomInt();
            } while (mChannelMap.containsKey(channelId));
            mChannelMap.put(channelId, null);
        }
        return channelId;
    }

    void register(int channelId, StreamChannel channel) {
        synchronized (mChannelMap) {
            mChannelMap.put(channelId, channel);
        }
    }

    void unregisterAndDisconnect(int channelId, StreamChannel channel) {
        synchronized (mChannelMap) {
            StreamChannel existing = mChannelMap.get(channelId);
            if (existing == null || existing == channel) {
                mChannelMap.remove(channelId);
            }
        }
        if (channel != null) {
            channel.disconnect();
        }
    }

    void doPingCheck() {
        if (!mPinged) {
            try {
                close("Broker is closed: Ping failure");
            } catch (IOException e) {
                // Ignore.
            }
        } else {
            mPinged = false;
        }
    }

    Lock closeLock() throws IOException {
        Lock lock = mCloseLock.readLock();
        lock.lock();
        if (mClosed) {
            lock.unlock();
            throw new IOException(mClosedReason);
        }
        return lock;
    }

    public void close() throws IOException {
        close(null);
    }

    void close(String reason) throws IOException {
        Lock lock = mCloseLock.writeLock();
        lock.lock();
        try {
            if (mClosed) {
                return;
            }

            mClosed = true;

            if (reason == null) {
                reason = "Broker is closed";
            }
            mClosedReason = reason;

            preClose();

            ScheduledFuture<?> pingCheckTask = mPingCheckTask;
            if (pingCheckTask != null) {
                pingCheckTask.cancel(true);
            }

            AcceptListener<StreamChannel> listener;
            while ((listener = mListenerQueue.poll()) != null) {
                listener.failed(new IOException(reason));
            }

            IOException exception = null;

            try {
                closeControlChannel();
            } catch (IOException e) {
                exception = e;
            }

            synchronized (mChannelMap) {
                for (StreamChannel channel : mChannelMap.values()) {
                    try {
                        channel.getOutputStream().flush();
                    } catch (IOException e) {
                        // Ignore.
                    } finally {
                        channel.disconnect();
                    }
                }

                mChannelMap.clear();
            }

            if (exception != null) {
                throw exception;
            }
        } finally {
            lock.unlock();
        }
    }

    abstract void preClose() throws IOException;

    abstract void closeControlChannel() throws IOException;

    private static class PingCheckTask extends AbstractPingTask<AbstractStreamBroker> {
        PingCheckTask(AbstractStreamBroker broker) {
            super(broker);
        }

        public void run() {
            AbstractStreamBroker broker = broker();
            if (broker != null) {
                broker.doPingCheck();
            }
        }
    }
}