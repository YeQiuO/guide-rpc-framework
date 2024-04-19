package github.javaguide.provider;

import github.javaguide.config.RpcServiceConfig;

/**
 * zk的对外接口，后面我们要干什么只需要通过单例模式获得这个类，然后用这个类的方法
 *
 * @author shuang.kou
 * @createTime 2020年05月31日 16:52:00
 */
public interface ServiceProvider {

    /**
     * 添加到本地 serviceMap 中（远程Rpc调用，获取本地的调用类）
     */
    void addService(RpcServiceConfig rpcServiceConfig);

    Object getService(String rpcServiceName);

    /**
     * 注册服务到 zookeeper 中（服务发现与注册）
     */
    void publishService(RpcServiceConfig rpcServiceConfig);

}
