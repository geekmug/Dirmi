/*
 *  Copyright 2007 Brian S O'Neill
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

package dirmi.io;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.net.Socket;
import java.net.SocketAddress;

/**
 * Basic connection implementation that wraps a socket.
 *
 * @author Brian S O'Neill
 */
public class SocketConnection implements Connection {
    private final Socket mSocket;

    public SocketConnection(Socket socket) throws IOException {
        mSocket = socket;
    }

    public InputStream getInputStream() throws IOException {
        return mSocket.getInputStream();
    }

    public int getReadTimeout() throws IOException {
        int timeout = mSocket.getSoTimeout();
        if (timeout == 0) {
            timeout = -1;
        }
        return timeout;
    }

    public void setReadTimeout(int timeoutMillis) throws IOException {
        if (timeoutMillis <= 0) {
            if (timeoutMillis < 0) {
                timeoutMillis = 0;
            } else {
                timeoutMillis = 1;
            }
        }
        mSocket.setSoTimeout(timeoutMillis);
    }

    public OutputStream getOutputStream() throws IOException {
        return mSocket.getOutputStream();
    }

    public int getWriteTimeout() throws IOException {
        // FIXME: may need to use Selector
        return -1;
    }

    public void setWriteTimeout(int timeoutMillis) throws IOException {
        // FIXME: may need to use Selector
    }

    public String getLocalAddressString() {
        SocketAddress addr = mSocket.getLocalSocketAddress();
        return addr == null ? null : addr.toString();
    }

    public String getRemoteAddressString() {
        SocketAddress addr = mSocket.getRemoteSocketAddress();
        return addr == null ? null : addr.toString();
    }

    public void close() throws IOException {
        mSocket.close();
    }
}
