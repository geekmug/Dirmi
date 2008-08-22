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

package dirmi.nio2;

import java.io.Closeable;
import java.io.IOException;

import java.nio.ByteBuffer;

/**
 * 
 *
 * @author Brian S O'Neill
 */
public interface MessageSender extends Closeable {
    /**
     * Send a fixed size message, possibly blocking if send buffer is full.
     *
     * @param buffer buffer whose position is set to the start of the message
     * and the remaining amount is the message size
     * @throws IllegalArgumentException if message is too large or less than
     * one byte
     */
    void send(ByteBuffer buffer) throws IOException;

    /**
     * Returns the constant maximum message size supported by the message channel.
     */
    int getMaximumMessageSize();

    /**
     * @return local address or null if unknown
     */
    Object getLocalAddress();

    /**
     * @return remote address or null if unknown
     */
    Object getRemoteAddress();
}
