package jp.co.disney.spplogin.service;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

import jp.co.disney.spp.v3.core.common.util.JwtSign;
import jp.co.disney.spplogin.exception.SppMemberRegisterException;
import jp.co.disney.spplogin.vo.DidMemberDetails;
import jp.co.disney.spplogin.vo.SppMemberDetails;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CoreWebApiService {

	@Value("${spplogin.core-webapi.base-url}")
	private String baseUrl;

	@Value("${spplogin.core-webapi.port}")
	private String port;

	@Value("${spplogin.core-webapi.cor-901.redirect-url}")
	private String redirectUrl;

	@Value("${spplogin.core-webapi.cor-901.path}")
	private String cor901path;

	@Value("${spplogin.core-webapi.cor-901.client-id}")
	private String clientId;

	@Value("${spplogin.core-webapi.cor-901.nonce}")
	private String nonce;

	@Value("${spplogin.core-webapi.cor-901.response-type}")
	private String responseType;

	@Value("${spplogin.core-webapi.cor-901.scope}")
	private String scope;

	@Value("${spplogin.core-webapi.cor-112.path}")
	private String cor112path;

	@Value("${spplogin.core-webapi.cor-001.path}")
	private String cor001path;

	@Autowired
	private RestTemplate restTemplate;

	/**
	 * <pre>
	 * COR-901 ??????????????????
	 * </pre>
	 * 
	 * @param memberNameOrEmailAddr ????????????????????????????????????????????????
	 * @param password??????????????????
	 * @param userAgent??????????????????????????????
	 */
	public ResponseEntity<String> authorize(String memberNameOrEmailAddr, String password, String userAgent, String dspp) {

		final Map<String, String> openidRequest = new HashMap<>();
		openidRequest.put("member_name", memberNameOrEmailAddr);
		openidRequest.put("password", password);
		openidRequest.put("client_id", clientId);
		openidRequest.put("nonce", nonce);

		String jwtRequest;

		try {
			final String jsonRequest = new ObjectMapper().writeValueAsString(openidRequest);

			log.debug("JSON Request: {}", jsonRequest);

			jwtRequest = JwtSign.getInstance().sign(jsonRequest);

			log.debug("JWT Request: {}", jwtRequest);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		final URI url = UriComponentsBuilder
				.fromUriString(baseUrl)
				.path(cor901path)
				.port(port)
				.queryParam("response_type", responseType)
				.queryParam("client_id", clientId)
				.queryParam("redirect_uri", redirectUrl)
				.queryParam("scope", scope)
				.queryParam("state", dspp)
				.queryParam("nonce", nonce)
				.queryParam("request", jwtRequest)
				.build()
				.encode()
				.toUri();


		log.debug("COR-901 Request URL: {}", url.toString());

		final HttpHeaders headers = new HttpHeaders();
		headers.set("User-Agent", userAgent);
		final HttpEntity<?> entity = new HttpEntity<>(headers);

		final ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

		log.debug("Response Status : {}", response.getStatusCode());
		log.debug("Location : {}", response.getHeaders().getLocation());

		return response;
	}

	/**
	 * <pre>
	 * COR-112 DID??????????????????
	 * </pre>
	 * @param didToken DID????????????
	 * @return DID??????????????????
	 */
	public DidMemberDetails getDidInformation(String didToken) {
		final URI url = UriComponentsBuilder
				.fromUriString(baseUrl)
				.path(cor112path)
				.port(port)
				.queryParam("did_token", didToken)
				.build()
				.encode()
				.toUri();

		log.debug("COR-112 Request URL : {}", url.toString());

		try {
			final ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);

			log.debug("Response Status : {} {}", response.getStatusCode(), response.getStatusCode().getReasonPhrase());
			log.debug("Response Body : {}", response.getBody());

			if(response.getStatusCode().is4xxClientError()) {
				log.error("DID??????????????????(COR-112)??????????????????????????????????????????????????? : {}", response.getBody());
				throw new RuntimeException("DID??????????????????(COR-112)???????????????????????????????????????????????????");
			}

			final ObjectMapper mapper = new ObjectMapper();
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);

			Cor112Response cor112Response =  mapper.readValue(response.getBody(), Cor112Response.class); 

			log.debug("COR-112 Response : {}", cor112Response);

			return cor112Response.getDidMemberDetails();

		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	/**
	 * <pre>
	 * COR-001 SPP????????????????????????
	 * </pre>
	 * @param sppMemberDetail ??????????????????
	 * @param actual ?????????????????????
	 * @param isFreshForDid DID?????????????????????
	 * @param didToken DID????????????????????????False???????????????
	 */
	public SppMemberDetails registerSppMember(SppMemberDetails sppMemberDetail, Boolean actual, Boolean isFreshForDid, String didToken) {

		final ResponseEntity<String> response = cor001(sppMemberDetail, actual, isFreshForDid, didToken);

		final ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
		mapper.setSerializationInclusion(Include.NON_NULL);

		Cor001Response cor001Response = null;

		try {
			if(response.getStatusCode().is4xxClientError()){
				Cor001ErrorResponse errorResponse = mapper.readValue(response.getBody(), Cor001ErrorResponse.class);
				log.error("SPP??????????????????(COR-001)??????????????????????????????????????????????????? : {}", response.getBody());
				throw new SppMemberRegisterException((Map<String, String>) errorResponse.error);
			}
			
			cor001Response = mapper.readValue(response.getBody(), Cor001Response.class);
			
		} catch (IOException e) {
			new RuntimeException(e);
		}

		log.debug("COR-001 Response : {}", cor001Response);

		return cor001Response.getSppMemberDetails();
	}

	private ResponseEntity<String> cor001(SppMemberDetails sppMemberDetail, boolean actual, boolean isFreshForDid, String didToken) {
		final URI url = UriComponentsBuilder
				.fromUriString(baseUrl)
				.port(port)
				.path(cor001path)
				.queryParam("actual", actual ? 1 : 0)
				.queryParam("did_token", didToken)
				.build()
				.encode()
				.toUri();

		log.debug("COR-001 Request URL : {}", url.toString());

		final ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
		mapper.setSerializationInclusion(Include.NON_NULL);

		Cor001Request req = new Cor001Request();
		SppMemberRegister register = new SppMemberRegister();
		register.setSppMemberDetails(sppMemberDetail);
		register.setIsFreshForDid(isFreshForDid);
		req.setSppMemberRegister(register);

		String requestJson;
		try {
			requestJson = mapper.writeValueAsString(req);
			log.debug("Request Body : {}", requestJson);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

		final HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON_UTF8);

		final HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);
		final ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

		log.debug("Response status : {} {}", response.getStatusCode(), response.getStatusCode().getReasonPhrase());
		log.debug("Response Body : {}", response.getBody());

		return response;
	}

	/**
	 * COR-001 ???????????????
	 */
	@Data
	@ToString
	private static class Cor001Request {
		private SppMemberRegister sppMemberRegister;

	}

	/**
	 * COR-001?????????????????????
	 *
	 */
	@Data
	@ToString
	private static class Cor001Response {
		private String status;
		private SppMemberDetails sppMemberDetails;
	}
	
	/**
	 * COR-001????????????????????????
	 *
	 */
	@Data
	@ToString
	private static class Cor001ErrorResponse {
		private String status;
		private Map<String, String> error;
	}

	/**
	 * SPP??????????????????
	 */
	@Data
	@ToString
	private static class SppMemberRegister {
		private Boolean isFreshForIur;
		private Boolean isFreshForDid;
		private SppMemberDetails sppMemberDetails;
		private String emailAddress;
		private Boolean mdmAgreement;
		private Boolean guardianActivateFlag;

	}

	/**
	 * COR-112?????????????????????
	 */
	@Data
	@ToString
	private static class Cor112Response {
		private DidMemberDetails didMemberDetails;
	}
}