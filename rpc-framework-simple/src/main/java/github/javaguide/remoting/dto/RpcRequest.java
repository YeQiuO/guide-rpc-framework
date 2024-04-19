package github.javaguide.remoting.dto;

import lombok.*;

import java.io.Serializable;

/**
 * @author shuang.kou
 * @createTime 2020年05月10日 08:24:00
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@ToString
public class RpcRequest implements Serializable {
    private static final long serialVersionUID = 1905122041950251207L;
    // RPC请求的唯一标识符
    private String requestId;
    // RPC调用的接口名称
    private String interfaceName;
    // 要调用的方法名称
    private String methodName;
    // 要传递给方法的参数数组
    private Object[] parameters;
    // 与参数对应的参数类型数组
    private Class<?>[] paramTypes;
    // 字段（服务版本）主要是为后续不兼容升级提供可能
    private String version;
    // 用于处理一个接口有多个类实现的情况
    private String group;

    public String getRpcServiceName() {
        return this.getInterfaceName() + this.getGroup() + this.getVersion();
    }
}
