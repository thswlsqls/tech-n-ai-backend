package com.tech.n.ai.domain.aurora.util;

import jakarta.persistence.EntityManager;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * ApplicationContextProvider
 */
@Component
public class ApplicationContextProvider implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ApplicationContextProvider.applicationContext = applicationContext;
    }

    public static EntityManager getEntityManager() {
        if (applicationContext == null) {
            return null;
        }
        try {
            return applicationContext.getBean(EntityManager.class);
        } catch (Exception e) {
            return null;
        }
    }
}
