package org.esupportail.claExternalID;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchResult;


import org.apache.commons.logging.LogFactory;

import static org.esupportail.claExternalID.Utils.*;
import com.google.gson.Gson;
import java.io.IOException;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;


import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Collections;

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
            String redirectUrl = conf.cas_base_url;



            ArrayList<String> fcAttrs = new ArrayList<String>() ;

            if(request.getServletPath().equals("/associate/")){
                String externalID=(String)session.getAttribute("externalID");
                String remoteUser=request.getRemoteUser();
                
                log.debug("On page '/associate/' externalID="+externalID+" remoteUser="+remoteUser);
                if(externalID!=null && remoteUser!=null && !externalID.equals(remoteUser)) {
                    if(!ldap.hasAttribute(remoteUser, conf.refId_attribute, conf.refId_prefix+externalID)){
                        ldap.addAttribute(remoteUser, conf.refId_attribute, conf.refId_prefix+externalID);
                    }
                } else {
                    log.debug("externalID "+externalID+" does not associate to LDAP id "+remoteUser);
                }

                if(target!=null) {
                    redirectUrl = target;
                }

            } else {
                String remoteUser = request.getParameter("principal");

                /***** FC attrs ******/
              String given_name = request.getParameter("given_name");
              String email = request.getParameter("email");
              String gender = request.getParameter("gender");
              String family_name = request.getParameter("family_name");
              String birthdate = request.getParameter("birthdate");
              //String preferred_username = request.getParameter("preferred_username");


              if (gender.equals("male"))
                gender = "M.";
                else
                gender = "Mme";


              String[] birthD = birthdate.split("-");
              Calendar calendar = Calendar.getInstance();
              int year = Integer.valueOf(birthD[0]);
              int month = Integer.valueOf(birthD[1]);
              int day = Integer.valueOf(birthD[2]);
              calendar.set(year,month-1,day,0,0,0);
              SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss'Z'");
              birthdate = dateFormat.format(calendar.getTime());
              log.debug(dateFormat.format(calendar.getTime()));

              fcAttrs.add(given_name);
              fcAttrs.add(email);
              fcAttrs.add(gender);
              fcAttrs.add(family_name);
              fcAttrs.add(birthdate);
            //fcAttrs.add(preferred_username);



              for(String fcAttr : fcAttrs ){
                  log.debug("fcAttr---"+fcAttr);
              }
              /******** ********/
                session.setAttribute("externalID", remoteUser);
                log.debug("On page '/' remoteUser="+remoteUser);
                session.removeAttribute(org.jasig.cas.client.util.AbstractCasFilter.CONST_CAS_ASSERTION);
                //Récupérer les attributs LDAP depuis le mail transmis par FC
               //Puis faire la comparaison avec la pseudo-ID pivot (Nom de naissance, prénom, sexe, date de naissance) + mail  --> LDAP(sn, givenName,   ,up1BirthDay) --> FC(family_name, given_name, gender, birthdate)
                if(target!=null && !target.contains(request.getRequestURL()) && !request.getRequestURL().toString().contains(target) && remoteUser!=null){
                  String searchFilter = "(&("+conf.ldap.birthdate+"="+birthdate+")("+conf.ldap.mailPerso+"="+email+")("+conf.ldap.civility+"="+gender+")("+conf.ldap.family_name+"="+family_name+")("+conf.ldap.given_name+"="+given_name+"))";
                 ArrayList<? extends SearchResult> res = Collections.list(ldap.getEntryForReconciliation(searchFilter));
                 if(res.size() == 1){
                      log.debug("Successful LDAP search, redirecting to target");
                      String uid=ldap.getSearchResult(res);
                      //Adding the FCsub to the corresponding LDAP entry then redirect to appropriate target
                      if(uid!=null) {
                          if(!ldap.hasAttribute(uid, conf.refId_attribute, remoteUser)){
                              ldap.addAttribute(uid, conf.refId_attribute, remoteUser);
                          }
                      } else {
                          log.debug("LDAP entry found has a uid which value is null");
                      }


                      if(target!=null) {
                          redirectUrl = target;
                      }

                 }else{
                     log.debug("Something went wrong during the LDAP search process");
                     log.info("Here is data retrieved from FC idp :  given_name="+given_name+"  | email="+email+"  | gender="+gender+"  | family_name="+family_name+"  | birthdate="+birthdate);
                     if(res.size() > 1){
                       log.info("The different entries retrieved");
                       ldap.getSearchResult(res);
                     }else{
                       log.info("No LDAP entries matching with provided data");
                     }
                    redirectUrl = "associate/?target=" + target;
                  }
                }
            }

	    log.debug("redirectUrl="+redirectUrl);
            if(redirectUrl == conf.cas_base_url){
                log.debug("No valid page or arguments, redirect to CAS");
            }
            log.debug("Redirect to " + redirectUrl);
	    redirectUrl=redirectUrl.replaceFirst("\\?target=.*\\?","?");
            response.sendRedirect(redirectUrl);
            
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

