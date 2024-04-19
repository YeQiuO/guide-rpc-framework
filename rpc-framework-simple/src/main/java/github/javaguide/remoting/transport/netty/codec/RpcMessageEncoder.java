package github.javaguide.remoting.transport.netty.codec;


import github.javaguide.compress.Compress;
import github.javaguide.enums.CompressTypeEnum;
import github.javaguide.enums.SerializationTypeEnum;
import github.javaguide.extension.ExtensionLoader;
import github.javaguide.remoting.constants.RpcConstants;
import github.javaguide.remoting.dto.RpcMessage;
import github.javaguide.serialize.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * <p>
 * custom protocol decoder
 * <p>
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
 *
 * @author WangTao
 * @createTime on 2020/10/2
 * @see <a href="https://zhuanlan.zhihu.com/p/95621344">LengthFieldBasedFrameDecoder解码器</a>
 */

@Slf4j
public class RpcMessageEncoder extends MessageToByteEncoder<RpcMessage> {
    // 为每个请求生成一个唯一的标识
    private static final AtomicInteger ATOMIC_INTEGER = new AtomicInteger(0);

    @Override
    protected void encode(ChannelHandlerContext ctx, RpcMessage rpcMessage, ByteBuf out) {
        try {
            // 写入魔术
            out.writeBytes(RpcConstants.MAGIC_NUMBER);
            // 写入版本
            out.writeByte(RpcConstants.VERSION);
            // 预留四个byte的地方给总长度
            out.writerIndex(out.writerIndex() + 4);
            // 得到消息类型
            byte messageType = rpcMessage.getMessageType();
            // 写入信息类型
            out.writeByte(messageType);
            // 写入序列化类型
            out.writeByte(rpcMessage.getCodec());
            // 写入压缩方式
            out.writeByte(CompressTypeEnum.GZIP.getCode());
            // 把唯一的，自增的int值，作为RequestId
            out.writeInt(ATOMIC_INTEGER.getAndIncrement());
            // 存放消息体，便于计算长度
            byte[] bodyBytes = null;
            // 初始化消息的总长度，初始值为头部长度
            int fullLength = RpcConstants.HEAD_LENGTH;
            // 如果不是心跳消息，总长度等于头+消息体
            if (messageType != RpcConstants.HEARTBEAT_REQUEST_TYPE
                    && messageType != RpcConstants.HEARTBEAT_RESPONSE_TYPE) {
                // serialize the object
                String codecName = SerializationTypeEnum.getName(rpcMessage.getCodec());
                log.info("codec name: [{}] ", codecName);
                // 还是一样的，用 extensionloadre 获取具体的序列化对象,
                Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class)
                        .getExtension(codecName);
                // 然后进行序列化
                bodyBytes = serializer.serialize(rpcMessage.getData());
                // 获取压缩实体类
                String compressName = CompressTypeEnum.getName(rpcMessage.getCompress());
                Compress compress = ExtensionLoader.getExtensionLoader(Compress.class)
                        .getExtension(compressName);
                // 压缩
                bodyBytes = compress.compress(bodyBytes);
                // 加上到总长度
                fullLength += bodyBytes.length;
            }

            if (bodyBytes != null) {
                out.writeBytes(bodyBytes);
            }
            // 获取当前的写入位置
            int writeIndex = out.writerIndex();
            // 移动到预留空间的位置
            out.writerIndex(writeIndex - fullLength + RpcConstants.MAGIC_NUMBER.length + 1);
            // 把总长度的值写到对应的地方
            out.writeInt(fullLength);
            // 还原写入位置
            out.writerIndex(writeIndex);
        } catch (Exception e) {
            log.error("Encode request error!", e);
        }

    }


}

