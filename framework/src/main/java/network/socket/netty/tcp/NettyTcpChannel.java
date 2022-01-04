package network.socket.netty.tcp;

import instance.BaseEnvironment;
import instance.DebugLevel;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import network.socket.netty.NettyChannel;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class NettyTcpChannel extends NettyChannel {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    private final EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ServerBootstrap serverBootstrap;
    private Bootstrap bootstrap = null;
    private Channel listenChannel = null;
    private Channel connectChannel = null;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public NettyTcpChannel(BaseEnvironment baseEnvironment, long sessionId, int threadCount, int sendBufSize, int recvBufSize, ChannelInitializer<SocketChannel> childHandler) {
        super(baseEnvironment, sessionId, threadCount, sendBufSize, recvBufSize);

        bossGroup = new NioEventLoopGroup();
        if (sendBufSize == 0) {
            workerGroup = new NioEventLoopGroup();
            serverBootstrap = new ServerBootstrap();

            serverBootstrap.group(bossGroup, workerGroup);
            serverBootstrap.channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_RCVBUF, recvBufSize)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                    .childHandler(childHandler);
            bootstrap = null;
            baseEnvironment.printMsg("[%s] NettyTcpChannel is recv-only type.", sessionId);
        } else {
            bootstrap.group(bossGroup).channel(SocketChannel.class)
                    .option(ChannelOption.SO_BROADCAST, false)
                    .option(ChannelOption.SO_SNDBUF, sendBufSize)
                    .option(ChannelOption.SO_RCVBUF, recvBufSize)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                    .handler(childHandler);
            baseEnvironment.printMsg("[%s] NettyTcpChannel is send & recv type.", sessionId);
        }
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // FUNCTIONS
    @Override
    public void stop () {
        getRecvBuf().clear();
        getSendBuf().clear();
        closeConnectChannel();
        closeListenChannel();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    @Override
    public Channel openListenChannel(String ip, int port) {
        if (listenChannel != null) {
            getBaseEnvironment().printMsg(DebugLevel.WARN, "Channel is already opened.");
            return null;
        }

        InetAddress address;
        ChannelFuture channelFuture;

        try {
            address = InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            getBaseEnvironment().printMsg(DebugLevel.WARN, "UnknownHostException is occurred. (ip=%s) (%s)", ip, e.toString());
            return null;
        }

        try {
            channelFuture = serverBootstrap.bind(address, port).sync();
            this.listenChannel = channelFuture.channel();
            getBaseEnvironment().printMsg("Channel is opened. (ip=%s, port=%s)", address, port);

            return this.listenChannel;
        } catch (Exception e) {
            getBaseEnvironment().printMsg(DebugLevel.WARN, "Channel is interrupted. (address=%s:%s) (%s)", ip, port, e.toString());
            return null;
        }
    }

    @Override
    public void closeListenChannel() {
        if (listenChannel == null) { return; }

        listenChannel.close();
        listenChannel = null;
    }

    @Override
    public Channel openConnectChannel(String ip, int port) {
        if (bootstrap == null) { return null; }

        try {
            InetAddress address = InetAddress.getByName(ip);
            ChannelFuture channelFuture = bootstrap.connect(address, port).sync();
            Channel channel =  channelFuture.channel();
            connectChannel = channel;
            return channel;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void closeConnectChannel() {
        if (connectChannel == null) { return; }

        connectChannel.close();
        connectChannel = null;
    }

    @Override
    public void sendData(byte[] data, int dataLength) {
        if (connectChannel == null || connectChannel.isActive()) { return; }

        ByteBuf buf = Unpooled.copiedBuffer(data);
        connectChannel.writeAndFlush(buf);
    }

    @Override
    public boolean isRecvOnly() {
        return bootstrap == null;
    }
    ////////////////////////////////////////////////////////////

}
