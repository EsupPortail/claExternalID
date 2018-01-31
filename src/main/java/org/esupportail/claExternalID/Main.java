package org.esupportail.claExternalID;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;


import org.apache.commons.logging.LogFactory;

import static org.esupportail.claExternalID.Utils.*;
import com.google.gson.Gson;
import java.io.IOException;

import java.util.Enumeration;

@SuppressWarnings("serial")
public class Main extends HttpServlet {           
    
    org.apache.commons.logging.Log log = LogFactory.getLog(Main.class);
    Conf conf = null;
    Ldap ldap;
    
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        if (conf == null) initConf(request);
        try {
            HttpSession session=request.getSession();
            String target=request.getParameter("target");
            
            if(request.getServletPath().equals("/associate/")){
                String externalID=(String)session.getAttribute("externalID");
                String remoteUser=request.getRemoteUser();
                
                log.debug("On page '/associate/' externalID="+externalID+" remoteUser="+remoteUser);
                if(externalID!=null && remoteUser!=null && !externalID.equals(remoteUser)) {
                    ldap.addAttribute(remoteUser, conf.refId_attribute, conf.refId_prefix+externalID);
                } else log.debug("externalID "+externalID+" does not associate to LDAP id "+remoteUser);
                
                if(target!=null) {
                    log.debug("Redirect to " + target);
                    response.sendRedirect(target);
                }
            
            } else {
                String remoteUser = request.getParameter("principal");
                session.setAttribute("externalID", remoteUser);
                log.debug("On page '/' remoteUser="+remoteUser);
                
                if(target!=null && !target.contains(request.getRequestURL()) && !request.getRequestURL().toString().contains(target) && remoteUser!=null){
                    log.debug("Redirect to associate/?target=" + target);
                    response.sendRedirect("associate/?target="+target);
                }
                
                if(target==null && remoteUser!=null) {
                    log.debug("Redirect to [associate/]");
                    response.sendRedirect("associate/");
                }
            }
        } catch (Exception e) {
            log.error(e);
        }
    }

    synchronized void initConf(HttpServletRequest request) {
        ServletContext sc = request.getServletContext();
        conf = getConf(sc);
        ldap = new Ldap(conf.ldap);
    }

    static Conf getConf(ServletContext sc) {
        Gson gson = new Gson();
        Conf conf = gson.fromJson(getConf(sc, "config.json", true), Conf.class);
        conf.init();
        return conf;
    }

    static String getConf(ServletContext sc, String jsonFile, boolean mustExist) {
        String s = file_get_contents(sc, "WEB-INF/" + jsonFile, mustExist);
        if (s == null) return "{}"; // do not fail here, checks are done on required attrs
        // allow trailing commas
        s = s.replaceAll(",(\\s*[\\]}])", "$1");
        return s;
    }
}

