package github.javaguide.remoting.transport.netty.codec;

import github.javaguide.compress.Compress;
import github.javaguide.enums.CompressTypeEnum;
import github.javaguide.enums.SerializationTypeEnum;
import github.javaguide.extension.ExtensionLoader;
import github.javaguide.remoting.constants.RpcConstants;
import github.javaguide.remoting.dto.RpcMessage;
import github.javaguide.remoting.dto.RpcRequest;
import github.javaguide.remoting.dto.RpcResponse;
import github.javaguide.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * custom protocol decoder
 * <pre>
 *   0     1     2     3     4        5     6     7     8         9          10      11     12  13  14   15 16
 *   +-----+-----+-----+-----+--------+----+----+----+------+-----------+-------+----- --+-----+-----+-------+
 *   |   magic   code        |version | full length         | messageType| codec|compress|    RequestId       |
 *   +-----------------------+--------+---------------------+-----------+-----------+-----------+------------+
 *   |                                                                                                       |
 *   |                                         body                                                          |
 *   |                                                                                                       |
 *   |                                        ... ...                                                        |
 *   +-------------------------------------------------------------------------------------------------------+
 * 4B  magic code（魔法数）   1B version（版本）   4B full length（消息长度）    1B messageType（消息类型）
 * 1B compress（压缩类型） 1B codec（序列化类型）    4B  requestId（请求的Id）
 * body（object类型数据）
 * </pre>
 * <p>
 * {@link LengthFieldBasedFrameDecoder} is a length-based decoder , used to solve TCP unpacking and sticking problems.
 * </p>
 *
 * @author wangtao
 * @createTime on 2020/10/2
 * @see <a href="https://zhuanlan.zhihu.com/p/95621344">LengthFieldBasedFrameDecoder解码器</a>
 */
@Slf4j
public class RpcMessageDecoder extends LengthFieldBasedFrameDecoder {
    public RpcMessageDecoder() {
        // lengthFieldOffset: magic code is 4B, and version is 1B, and then full length. so value is 5
        // lengthFieldLength: full length is 4B. so value is 4
        // lengthAdjustment: full length include all data and read 9 bytes before, so the left length is (fullLength-9). so values is -9
        // initialBytesToStrip: we will check magic code and version manually, so do not strip any bytes. so values is 0
        this(RpcConstants.MAX_FRAME_LENGTH, 5, 4, -9, 0);
    }

    /**
     * @param maxFrameLength      最大帧长度，指定能够处理的帧的最大长度。如果帧的长度超过这个值，将抛出
     * @param lengthFieldOffset   长度字段偏移量，指定长度字段在帧中的偏移量。在这里，魔法数占4个字节，版本号占1个字节，然后是消息总长度字段，因此长度字段的偏移量是5。也就是说，下标5后面，是长度字段
     * @param lengthFieldLength   长度字段的字节长度，指定长度字段占用几个字节。在这里，消息总长度字段占4个字节，所以长度字段的字节长度是4
     * @param lengthAdjustment    长度调整值，指定长度字段的补偿值，用于计算实际的消息长度。
     * @param initialBytesToStrip 跳过的初始字节数，指定从解码帧中去掉的字节数，这些字节包括长度字段本身。
     *                            If you need to receive all of the header+body data, this value is 0
     *                            if you only want to receive the body data, then you need to skip the number of bytes consumed by the header.
     */
    public RpcMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
                             int lengthAdjustment, int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        // 调用了父类LengthFieldBasedFrameDecoder的decode方法，获得解码后的结果。LengthFieldBasedFrameDecoder会根据长度字段的信息，将输入的ByteBuf切割成一帧一帧的消息。
        Object decoded = super.decode(ctx, in);
        // 检查decoded是否是ByteBuf的实例。如果是，说明解码成功，获取到了一帧完整的消息。
        if (decoded instanceof ByteBuf) {
            // 将解码后的结果强制转换为ByteBuf类型，方便后续处理
            ByteBuf frame = (ByteBuf) decoded;
            // 这里检查帧的可读字节数是否大于或等于 RpcConstants.TOTAL_LENGTH，即消息的总长度。这是为了确保帧中包含了完整的消息。
            if (frame.readableBytes() >= RpcConstants.TOTAL_LENGTH) {
                try {
                    // 如果帧中包含完整的消息，调用decodeFrame方法进行进一步的解码
                    return decodeFrame(frame);
                } catch (Exception e) {
                    log.error("Decode frame error!", e);
                    throw e;
                } finally {
                    // 在finally块中释放frame，确保不会造成内存泄漏
                    frame.release();
                }
            }

        }
        // 如果帧中不包含完整的消息，则直接返回上一层解码器的结果，等待更多数据的到来
        return decoded;
    }


    private Object decodeFrame(ByteBuf in) {
        // 检查魔术号
        checkMagicNumber(in);
        // 检查版本
        checkVersion(in);
        // 传输协议里，消息长度四个字节，正好一个int
        int fullLength = in.readInt();
        // build RpcMessage object
        // 一个字节: 消息类型
        byte messageType = in.readByte();
        // 一个字节: 序列化类型
        byte codecType = in.readByte();
        // 一个字节: 压缩类型
        byte compressType = in.readByte();
        // 一个字节: 请求的Id
        int requestId = in.readInt();
        RpcMessage rpcMessage = RpcMessage.builder()
                .codec(codecType)
                .requestId(requestId)
                .messageType(messageType).build();
        // 处理心跳消息
        if (messageType == RpcConstants.HEARTBEAT_REQUEST_TYPE) {
            rpcMessage.setData(RpcConstants.PING);
            return rpcMessage;
        }
        if (messageType == RpcConstants.HEARTBEAT_RESPONSE_TYPE) {
            rpcMessage.setData(RpcConstants.PONG);
            return rpcMessage;
        }
        // 处理非心跳消息的数据解析
        // 计算消息体的长度
        int bodyLength = fullLength - RpcConstants.HEAD_LENGTH;
        if (bodyLength > 0) {
            byte[] bs = new byte[bodyLength];
            in.readBytes(bs);
            // 获取压缩算法的实例
            String compressName = CompressTypeEnum.getName(compressType);
            Compress compress = ExtensionLoader.getExtensionLoader(Compress.class)
                    .getExtension(compressName);
            // 按这个实例去解压缩
            bs = compress.decompress(bs);
            // 通过 SerializationTypeEnum 中的方法获取给定序列化类型的名称。SerializationTypeEnum 应该是一个枚举，其中包含了各种序列化算法的类型
            String codecName = SerializationTypeEnum.getName(rpcMessage.getCodec());
            log.info("codec name: [{}] ", codecName);
            // 扩展加载器模式。ExtensionLoader 加载了实现了 Serializer 接口的序列化算法的具体实现类。
            Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class)
                    .getExtension(codecName);
            if (messageType == RpcConstants.REQUEST_TYPE) {
                // 调用序列化算法的 deserialize 方法将接收到的字节数组 bs 反序列化为 RpcRequest 对象，并将其设置为 rpcMessage 的数据部分
                RpcRequest tmpValue = serializer.deserialize(bs, RpcRequest.class);
                rpcMessage.setData(tmpValue);
            } else {
                // 调用序列化算法的 deserialize 方法将接收到的字节数组 bs 反序列化为 RpcResponse 对象，并将其设置为 rpcMessage 的数据部分
                RpcResponse tmpValue = serializer.deserialize(bs, RpcResponse.class);
                rpcMessage.setData(tmpValue);
            }
        }
        return rpcMessage;
    }

    private void checkVersion(ByteBuf in) {
        // read the version and compare
        byte version = in.readByte();
        if (version != RpcConstants.VERSION) {
            throw new RuntimeException("version isn't compatible" + version);
        }
    }

    private void checkMagicNumber(ByteBuf in) {
        // read the first 4 bit, which is the magic number, and compare
        int len = RpcConstants.MAGIC_NUMBER.length;
        byte[] tmp = new byte[len];
        in.readBytes(tmp);
        for (int i = 0; i < len; i++) {
            if (tmp[i] != RpcConstants.MAGIC_NUMBER[i]) {
                throw new IllegalArgumentException("Unknown magic code: " + Arrays.toString(tmp));
            }
        }
    }

}
