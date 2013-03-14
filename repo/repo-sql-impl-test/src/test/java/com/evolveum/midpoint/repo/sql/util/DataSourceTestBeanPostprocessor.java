/*
 * Copyright (c) 2012 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2013 [name of copyright owner]
 */

package com.evolveum.midpoint.repo.sql.util;

import com.evolveum.midpoint.init.RepositoryFactory;
import com.evolveum.midpoint.repo.sql.SqlRepositoryConfiguration;
import com.evolveum.midpoint.repo.sql.testing.TestSqlRepositoryFactory;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * This bean post processor is only used in {@link com.evolveum.midpoint.repo.sql.DataSourceTest} because that
 * test class needs hibernate property hibernate.hbm2ddl.auto set to update. Test configuration can be overridden
 * through system option -Dconfig=<properties file> so this processor guarantees that hbm2ddl is always set to
 * "update".
 *
 * @author lazyman
 */
public class DataSourceTestBeanPostprocessor implements BeanPostProcessor, ApplicationContextAware {

    private static final Trace LOGGER = TraceManager.getTrace(DataSourceTestBeanPostprocessor.class);

    private ApplicationContext context;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof RepositoryFactory)) {
            return bean;
        }

        System.out.println("Changing hibernate.hbm2ddl.auto to update");
        LOGGER.info("Changing hibernate.hbm2ddl.auto to update");

        TestSqlRepositoryFactory factory = context.getBean("testSqlRepositoryFactory", TestSqlRepositoryFactory.class);
        SqlRepositoryConfiguration config = factory.getSqlConfiguration();
        config.setHibernateHbm2ddl("update");

        return bean;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }
}
