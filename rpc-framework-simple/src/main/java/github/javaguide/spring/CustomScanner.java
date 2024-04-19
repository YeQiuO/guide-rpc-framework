package github.javaguide.spring;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.annotation.Annotation;

/**
 * 通过继承 ClassPathBeanDefinitionScanner 来自定义扫描器
 *
 * @author shuang.kou
 * @createTime 2020年08月10日 21:42:00
 */
public class CustomScanner extends ClassPathBeanDefinitionScanner {

    public CustomScanner(BeanDefinitionRegistry registry, Class<? extends Annotation> annoType) {
        super(registry);
        // 添加扫描规则(要求扫描到的注解类型)
        super.addIncludeFilter(new AnnotationTypeFilter(annoType));
    }

    /**
     * 扫描带有特定注解的类，并且将他们注册为bean
     *
     * @param basePackages 要扫描的包路径
     * @return 扫描到的类的个数
     */
    @Override
    public int scan(String... basePackages) {
        return super.scan(basePackages);
    }
}