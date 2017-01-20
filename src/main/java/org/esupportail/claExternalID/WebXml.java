package org.esupportail.claExternalID;

import javax.servlet.*;

import org.jasig.cas.client.session.SingleSignOutFilter;
import org.jasig.cas.client.authentication.AuthenticationFilter;
import org.jasig.cas.client.util.HttpServletRequestWrapperFilter;
import org.jasig.cas.client.validation.Cas20ProxyReceivingTicketValidationFilter;

import static org.esupportail.claExternalID.Utils.*;

public class WebXml implements ServletContextListener {
        
    public void contextDestroyed(ServletContextEvent event) {}

    public void contextInitialized(ServletContextEvent event) {
        configure(event.getServletContext());
    }

    private void configure(ServletContext sc) {
        Conf conf = Main.getConf(sc);
                
        addFilter(sc, "CAS Single Sign Out", SingleSignOutFilter.class,
                  asMap("casServerUrlPrefix", conf.cas_base_url),
                  "/");

        addFilter(sc, "CAS Authentication", AuthenticationFilter.class,
                  asMap("casServerLoginUrl", conf.cas_login_url)
                   .add("serverName", url2host(conf.claExternalID_url)),
                  "/","/associate/");

        addFilter(sc, "CAS Validate", Cas20ProxyReceivingTicketValidationFilter.class,
                  asMap("casServerUrlPrefix", conf.cas_base_url)
                   .add("serverName", url2host(conf.claExternalID_url))
                   .add("redirectAfterValidation", "false"), 
                  "/","/associate/");


        addFilter(sc, "CAS Request Wrapper", HttpServletRequestWrapperFilter.class, null,
                  "/","/associate/");
        
        addServlet(sc, "org.esupportail.claExternalID", Main.class, null, "/");
    }
}
