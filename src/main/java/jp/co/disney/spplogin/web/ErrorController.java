package jp.co.disney.spplogin.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * エラーページコントローラー
 *
 */
@Controller
public class ErrorController {

	@RequestMapping(value="/400", method=RequestMethod.GET)
	public String badRequest() {
		return "common/400";
	}
	
	@RequestMapping(value="/404", method=RequestMethod.GET)
	public String notFound() {
		return "common/404";
	}
	
	@RequestMapping(value="/405", method=RequestMethod.GET)
	public String methodNotAllowed() {
		return "common/405";
	}
	
	@RequestMapping(value="/500", method=RequestMethod.GET)
	public String internalServerError() {
		return "common/500";
	}
	
	
}
