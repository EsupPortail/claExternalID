package org.esupportail.claExternalID;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class Utils {
    
    static <V> MapBuilder<V> asMap(String key, V value) {
        return new MapBuilder<V>().add(key, value);
    }

    @SuppressWarnings("serial")
    static class MapBuilder<V> extends HashMap<String, V> {
        MapBuilder<V> add(String key, V value) {
            this.put(key, value);
            return this;
        }
    }

    static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static Log log() {
        return log(Utils.class);
    }
    
    static Log log(Class<?> clazz) {
        return LogFactory.getLog(clazz);
    }
    
    static URL toURL(String url) {
        try {
            return new URL(url);
        } catch (java.net.MalformedURLException e) {
            log().error(e, e);
            return null;
        }
    }

    static String url2host(String url) {
        URL url_ = toURL(url);
        return url_ != null ? url_.getHost() : null;
    }
    
    static String removePrefixOrNull(String s, String prefix) {
        return s.startsWith(prefix) ? s.substring(prefix.length()) : null;
    }
  
    static boolean hasParameter(HttpServletRequest request, String attrName) {
        return request.getParameter(attrName) != null;
    }

    static String file_get_contents_raw(ServletContext sc, String file) throws IOException {
        InputStream in = sc.getResourceAsStream("/" + file);
        if (in == null) throw new FileNotFoundException("error reading file " + file);
        return IOUtils.toString(in, "UTF-8");
    }

    static String file_get_contents(ServletContext sc, String file, boolean mustExist) {
        try {
            return file_get_contents_raw(sc, file);
        } catch (FileNotFoundException e) {
            if (mustExist) throw new RuntimeException(e);
            return null;
        } catch (IOException e) {
            log().error("error reading file " + file, e);
            return null;
        }
    }
    
    static String file_get_contents(HttpServletRequest request, String file) {
        return file_get_contents(request.getServletContext(), file, true);
    }

    static String file_get_contents(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), "UTF-8");
    }
        
    static void bad_request(HttpServletResponse response, String msg) {
        log().info(msg);
        try {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
        } catch (IOException e) {
            log().error(e);
        }
    }
    
    static void respond_js(HttpServletResponse response, String js) throws IOException {
        response.setContentType("application/javascript; charset=utf8");
        response.getWriter().write(js);
    }

    static void addFilter(ServletContext sc, String name, Class<? extends Filter> clazz, Map<String,String> params, String... urls) {
        FilterRegistration.Dynamic o = sc.addFilter(name, clazz);
        if (params != null) o.setInitParameters(params);
        o.addMappingForUrlPatterns(null, true, urls);
    }
        
    static void addServlet(ServletContext sc, String name, Class<? extends Servlet> clazz, Map<String,String> params, String... urls) {
        ServletRegistration.Dynamic o = sc.addServlet(name, clazz);
        if (params != null) o.setInitParameters(params);
        o.addMapping(urls);
    }
}
