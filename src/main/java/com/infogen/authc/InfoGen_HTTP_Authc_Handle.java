package com.infogen.authc;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.infogen.authc.configuration.Comparison;
import com.infogen.authc.configuration.InfoGen_Auth_Configuration;
import com.infogen.authc.configuration.handle.impl.Authc_Properties_Handle_Authc;
import com.infogen.authc.exception.InfoGen_Auth_Exception;
import com.infogen.authc.exception.impl.Authentication_Fail_Exception;
import com.infogen.authc.exception.impl.Session_Lose_Exception;
import com.infogen.authc.subject.Subject;
import com.infogen.core.json.Return;
import com.infogen.core.util.CODE;

/**
 * HTTP接口的API认证的处理器,可以通过ini配置注入使用的session管理器
 * 
 * @author larry
 * @email larry.lv.word@gmail.com
 */
public class InfoGen_HTTP_Authc_Handle {
	private static final Logger LOGGER = LogManager.getLogger(InfoGen_HTTP_Authc_Handle.class.getName());

	public static final String TOKEN_NAME = "x-access-token";
	// 初始化配置时赋值
	public static final List<Comparison> urls_rules = Authc_Properties_Handle_Authc.urls_rules;

	public String[] authc(String requestURI) {
		String[] roles = urls_equal.get(requestURI);
		if (roles == null) {
			for (String prefix : urls_startswith.keySet()) {
				if (requestURI.startsWith(prefix)) {
					return urls_startswith.get(prefix);
				}
			}
		} else {
			return roles;
		}
		return null;
	}

	/**
	 * js 前端页面加载时判断是否有 x-access-token 没有跳转到登录页面
	 * 
	 * ajax 调用后判断如果为没有权限执行登录操作
	 * 
	 * 只有存在 x-access-token 并通过有效期验证的才生成用于验证权限的subject
	 */
	public Boolean doFilter(String requestURI, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		// request.getRealPath("/")); F:\Tomcat 6.0\webapps\news\test
		// System.out.println(request.getRequestURL()); // http://localhost:8080/news/main/list.jsp
		// System.out.println(request.getContextPath()); // /news
		// System.out.println(request.getServletPath()); // /main/list.jsp 配置spring mvc 的<mvc:default-servlet-handler />后始终为空
		// System.out.println(request.getRequestURI()); // /news/main/list.jsp

		try {
			// 当前请求页面 判断是否需要认证
			String[] roles = authc(requestURI);

			// 该方法不需要任何角色验证直接返回认证成功
			if (roles == null) {
				return true;
			}

			// 认证
			String x_access_token = request.getParameter(TOKEN_NAME);
			if (x_access_token == null) {
				x_access_token = request.getHeader(TOKEN_NAME);
			}
			if (x_access_token == null || x_access_token.trim().isEmpty()) {
				throw new Authentication_Fail_Exception();
			}

			//
			Subject subject = InfoGen_Authc.load(x_access_token);
			if (subject == null) {
				throw new Session_Lose_Exception();
			}
			subject.checkExpiration();
			subject.hasRole(roles);

			//
			subject.setLast_access_time(Clock.system(InfoGen_Auth_Configuration.zoneid).millis());
			InfoGen_Authc.save(subject);
			// 缓存
			InfoGen_Authc.set(subject);
		} catch (InfoGen_Auth_Exception e) {
			LOGGER.info("认证失败:", e);
			response.sendRedirect("signin.html");
			response.getWriter().write(Return.FAIL(CODE.authentication_fail, e).toJson());
			return false;
		} catch (Exception e) {
			LOGGER.error("认证异常:", e);
			response.sendRedirect("signin.html");
			response.getWriter().write(Return.FAIL(CODE.error).toJson());
			return false;
		}
		return true;
	}
}