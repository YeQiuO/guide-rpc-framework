package github.javaguide.spring;

import github.javaguide.annotation.RpcReference;
import github.javaguide.annotation.RpcService;
import github.javaguide.config.RpcServiceConfig;
import github.javaguide.enums.RpcRequestTransportEnum;
import github.javaguide.extension.ExtensionLoader;
import github.javaguide.factory.SingletonFactory;
import github.javaguide.provider.ServiceProvider;
import github.javaguide.provider.impl.ZkServiceProviderImpl;
import github.javaguide.proxy.RpcClientProxy;
import github.javaguide.remoting.transport.RpcRequestTransport;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

/**
 * 在创建bean之前调用此方法，以查看类是否带注释
 *
 * @author shuang.kou
 * @createTime 2020年07月14日 16:42:00
 */
@Slf4j
@Component
// 实现 BeanPostProcessor 接口, spring 在初始化每个 Bean 的过程中都会调用 postProcessBeforeInitialization 和 postProcessAfterInitialization 方法
public class SpringBeanPostProcessor implements BeanPostProcessor {

    private final ServiceProvider serviceProvider;
    private final RpcRequestTransport rpcClient;

    public SpringBeanPostProcessor() {
        // zookeeper 提供服务注册的功能
        this.serviceProvider = SingletonFactory.getInstance(ZkServiceProviderImpl.class);
        // SPI实现 处理远程过程调用（启动 Netty 客户端去连接 Netty 服务端）
        this.rpcClient = ExtensionLoader.getExtensionLoader(RpcRequestTransport.class).getExtension(RpcRequestTransportEnum.NETTY.getName());
    }

    @SneakyThrows
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // System.out.println(beanName + "调用　BeanPostProcessor 的 postProcessAfterInitialization");
        if (bean.getClass().isAnnotationPresent(RpcService.class)) {
            log.info("[{}] is annotated with  [{}]", bean.getClass().getName(), RpcService.class.getCanonicalName());
            RpcService rpcService = bean.getClass().getAnnotation(RpcService.class);
            // 如果一个类上面有 @RpcService 注解，那么他就会通过 service(bean).build() 构造服务端代理类 config
            RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                    .group(rpcService.group())
                    .version(rpcService.version())
                    .service(bean).build();
            serviceProvider.publishService(rpcServiceConfig);
        }
        return bean;
    }

    /**
     * 将使用的远程服务(RpcReference)属性设置为代理对象, 隐藏底层通讯
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();
        Field[] declaredFields = targetClass.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            RpcReference rpcReference = declaredField.getAnnotation(RpcReference.class);
            if (rpcReference != null) {
                RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                        .group(rpcReference.group())
                        .version(rpcReference.version()).build();
                // 初始化 RpcClientProxy（实现了 InvocationHandler 接口，可以作为代理类）
                RpcClientProxy rpcClientProxy = new RpcClientProxy(rpcClient, rpcServiceConfig);
                // declaredField.getType() 返回 HelloService.class 接口
                // 生成动态代理类，代理了 HelloService 的方法，每当调用这个方法的时候，都会执行 rpcClientProxy.invoke()
                Object clientProxy = rpcClientProxy.getProxy(declaredField.getType());
                // 通过反射调用类的私有方法时，要先在这个私有方法对应的 Method 对象上调用 setAccessible(true) 来取消对这个方法的访问检查，再调用 invoke() 方法来执行这个私有方法
                declaredField.setAccessible(true);
                try {
                    // 将代理类 clientProxy 注入 bean 中被 @RpcReference 注释的字段（覆盖了原有的字段）
                    declaredField.set(bean, clientProxy);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

        }
        return bean;
    }
}
