package github.javaguide.spring;

import github.javaguide.annotation.RpcScan;
import github.javaguide.annotation.RpcService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 定义 BeanDefinition 注册器: 定义要扫描哪些包，扫描有哪些注解的类
 *
 * @author shuang.kou
 * @createTime 2020年08月10日 22:12:00
 */
@Slf4j
public class CustomScannerRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {
    private static final String SPRING_BEAN_BASE_PACKAGE = "github.javaguide";
    private static final String BASE_PACKAGE_ATTRIBUTE_NAME = "basePackage";
    private ResourceLoader resourceLoader;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        // ResourceLoader 为 Spring 资源加载的统一抽象，具体的资源加载则由相应的实现类来完成
        this.resourceLoader = resourceLoader;

    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry beanDefinitionRegistry) {
        // 先获取 RpcScan 的属性
        Map<String, Object> annotationAttributes = annotationMetadata.getAnnotationAttributes(RpcScan.class.getName());
        // 将属性封装为 AnnotationAttributes 对象
        AnnotationAttributes rpcScanAnnotationAttributes = AnnotationAttributes.fromMap(annotationAttributes);
        String[] rpcScanBasePackages = new String[0];
        // 如果 RpcScan 设置了包名,则扫描这个包
        if (rpcScanAnnotationAttributes != null) {
            // 获取注解的 basePackage 属性值
            rpcScanBasePackages = rpcScanAnnotationAttributes.getStringArray(BASE_PACKAGE_ATTRIBUTE_NAME);
        }
        // 否则扫描 RpcScan 注解的类所在的包
        if (rpcScanBasePackages.length == 0) {
            // getIntrospectedClass() 获取了使用 RpcScan 注解的这个类
            Class<?> introspectedClass = ((StandardAnnotationMetadata) annotationMetadata).getIntrospectedClass();
            // 获取这个类的包名
            String name = introspectedClass.getPackage().getName();
            rpcScanBasePackages = new String[]{name};
        }
        // 扫描 RpcService 的扫描器
        CustomScanner rpcServiceScanner = new CustomScanner(beanDefinitionRegistry, RpcService.class);
        // 扫描 Component 的扫描器
        CustomScanner springBeanScanner = new CustomScanner(beanDefinitionRegistry, Component.class);
        // [不确定]设置资源位置, 资源加载时可以直接定位到对应的资源
        if (resourceLoader != null) {
            rpcServiceScanner.setResourceLoader(resourceLoader);
            springBeanScanner.setResourceLoader(resourceLoader);
        }
        // 扫描带有特定注解的类，并且将他们注册为 bean
        int springBeanAmount = springBeanScanner.scan(SPRING_BEAN_BASE_PACKAGE);
        log.info("springBeanScanner扫描的数量 [{}]", springBeanAmount);
        int rpcServiceCount = rpcServiceScanner.scan(rpcScanBasePackages);
        log.info("rpcServiceScanner扫描的数量 [{}]", rpcServiceCount);

    }

}
