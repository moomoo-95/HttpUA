package network.socket.netty.tcp;

import instance.BaseEnvironment;
import instance.DebugLevel;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import network.socket.netty.NettyChannel;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class NettyTcpChannel extends NettyChannel {

    ////////////////////////////////////////////////////////////
    // VARIABLES
    private final EventLoopGroup eventLoopGroup;
    private final Bootstrap bootstrap;
    private Channel listenChannel = null;
    private Channel connectChannel = null;
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    public NettyTcpChannel(BaseEnvironment baseEnvironment, long sessionId, int threadCount, int recvBufSize, ChannelInitializer<NioSocketChannel> childHandler) {
        super(baseEnvironment, sessionId, threadCount, 0, recvBufSize);

        bootstrap = new Bootstrap();
        eventLoopGroup = new NioEventLoopGroup();
        bootstrap.group(eventLoopGroup).channel(NioSocketChannel.class)
                .option(ChannelOption.SO_RCVBUF, recvBufSize)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                .handler(childHandler);
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
        eventLoopGroup.shutdownGracefully();
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
            channelFuture = bootstrap.bind(address, port).sync();
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
    ////////////////////////////////////////////////////////////

}
