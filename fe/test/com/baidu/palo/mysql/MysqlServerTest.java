// Copyright (c) 2017, Baidu.com, Inc. All Rights Reserved

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.baidu.palo.mysql;

import com.baidu.palo.qe.ConnectContext;
import com.baidu.palo.qe.ConnectScheduler;

import org.junit.Assert;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SocketChannel;

public class MysqlServerTest {
    private static final Logger LOG = LoggerFactory.getLogger(MysqlServerTest.class);

    private int submitNum;
    private int submitFailNum;
    private ConnectScheduler scheduler;
    private ConnectScheduler badScheduler;

    @Before
    public void setUp() {
        submitNum = 0;
        submitFailNum = 0;
        scheduler = EasyMock.createMock(ConnectScheduler.class);
        EasyMock.expect(scheduler.submit(EasyMock.anyObject(ConnectContext.class)))
                .andAnswer(new IAnswer<Boolean>() {
                    @Override
                    public Boolean answer() throws Throwable {
                        LOG.info("answer.");
                        synchronized (MysqlServerTest.this) {
                            submitNum++;
                        }
                        return Boolean.TRUE;
                    }
                }).anyTimes();
        EasyMock.replay(scheduler);

        badScheduler = EasyMock.createMock(ConnectScheduler.class);
        EasyMock.expect(badScheduler.submit(EasyMock.anyObject(ConnectContext.class)))
                .andAnswer(new IAnswer<Boolean>() {
                    @Override
                    public Boolean answer() throws Throwable {
                        LOG.info("answer.");
                        synchronized (MysqlServerTest.this) {
                            submitFailNum++;
                        }
                        return Boolean.FALSE;
                    }
                }).anyTimes();
        EasyMock.replay(badScheduler);
    }

    @Test
    public void testNormal() throws IOException, InterruptedException {
        ServerSocket socket = new ServerSocket(0);
        int port = socket.getLocalPort();
        socket.close();

        MysqlServer server = new MysqlServer(port, scheduler);
        Assert.assertTrue(server.start());

        // submit
        SocketChannel channel = SocketChannel.open();
        channel.connect(new InetSocketAddress("127.0.0.1", port));
        // sleep to wait mock process
        Thread.sleep(2000);
        channel.close();

        // submit twice
        channel = SocketChannel.open();
        channel.connect(new InetSocketAddress("127.0.0.1", port));
        // sleep to wait mock process
        Thread.sleep(2000);
        channel.close();

        // stop and join
        server.stop();
        server.join();

        Assert.assertEquals(2, submitNum);
    }

    @Test
    public void testInvalidParam() throws IOException {
        ServerSocket socket = new ServerSocket(0);
        int port = socket.getLocalPort();
        socket.close();
        MysqlServer server = new MysqlServer(port, null);
        Assert.assertFalse(server.start());
    }

    @Test
    public void testBindFail() throws IOException {
        ServerSocket socket = new ServerSocket(0);
        int port = socket.getLocalPort();
        socket.close();
        MysqlServer server = new MysqlServer(port, scheduler);
        Assert.assertTrue(server.start());
        MysqlServer server1 = new MysqlServer(port, scheduler);
        Assert.assertFalse(server1.start());

        server.stop();
        server.join();
    }

    @Test
    public void testSubFail() throws IOException, InterruptedException {
        ServerSocket socket = new ServerSocket(0);
        int port = socket.getLocalPort();
        socket.close();
        MysqlServer server = new MysqlServer(port, badScheduler);
        Assert.assertTrue(server.start());

        // submit
        SocketChannel channel = SocketChannel.open();
        channel.connect(new InetSocketAddress(port));
        // sleep to wait mock process
        Thread.sleep(100);
        channel.close();

        // submit twice
        channel = SocketChannel.open();
        channel.connect(new InetSocketAddress(port));
        // sleep to wait mock process
        Thread.sleep(100);
        channel.close();

        // stop and join
        server.stop();
        server.join();

        Assert.assertEquals(2, submitFailNum);
    }

}