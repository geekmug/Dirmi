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

import java.io.Closeable;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.net.Socket;

/**
 * 
 *
 * @author Brian S O'Neill
 */
class SocketStreamChannel implements StreamChannel {
    private final Socket mSocket;

    SocketStreamChannel(Socket socket) throws IOException {
        mSocket = socket;
    }

    public InputStream getInputStream() throws IOException {
        return mSocket.getInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
        return mSocket.getOutputStream();
    }

    public Object getLocalAddress() {
        return mSocket.getLocalSocketAddress();
    }

    public Object getRemoteAddress() {
        return mSocket.getRemoteSocketAddress();
    }

    @Override
    public String toString() {
        return "SocketStreamChannel {localAddress=" + getLocalAddress() +
            ", remoteAddress=" + getRemoteAddress() + '}';
    }

    public void close() throws IOException {
        mSocket.close();
    }

    public void disconnect() {
        try {
            mSocket.close();
        } catch (IOException e) {
        }
    }

    public Closeable getCloseable() {
        // This is a bit frustrating. The Socket class should implement
        // Closeable, but it doesn't. I could return either streams of the
        // socket, but accessing them might throw an exception. So, wrap the
        // socket with a little class.
        return new Closeable() {
            public void close() throws IOException {
                mSocket.close();
            }
        };
    }
}