package com.example.samldecoder;

import com.example.samldecoder.exception.SamlDecoderException;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarEntry;
import de.sstoehr.harreader.model.HttpMethod;
import org.apache.commons.codec.binary.Base64;
import org.opensaml.Configuration;
import org.opensaml.DefaultBootstrap;
import org.opensaml.xml.ConfigurationException;
import org.json.JSONObject;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.impl.ResponseUnmarshaller;
import org.opensaml.xml.io.UnmarshallerFactory;
import org.opensaml.xml.parse.BasicParserPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RestImpl {
    private final Logger logger = LoggerFactory.getLogger(RestImpl.class);
    private final Base64 base64Decoder;
    private final UnmarshallerFactory unmarshallerFactory;
    private final BasicParserPool parserPool;
    
    private static final String SAML_RESPONSE_PREFIX = "SAMLResponse=";
    private static final String UAT_LOGIN_URL = "https://login-uat.fisglobal.com/idp/MemoCBAUAT/";
    private static final String PROD_LOGIN_URL = "https://login10.fisglobal.com/idp/CBA/";

    public RestImpl() {
        this.base64Decoder = new Base64();
        this.parserPool = new BasicParserPool();
        this.parserPool.setNamespaceAware(true);
        
        try {
            DefaultBootstrap.bootstrap();
            this.unmarshallerFactory = Configuration.getUnmarshallerFactory();
        } catch (ConfigurationException e) {
            logger.error("Failed to initialize OpenSAML library", e);
            throw new SamlDecoderException("Failed to initialize OpenSAML library", e);
        }
    }

    public String response(String requestBody) {
        logger.debug("Processing SAML response from request body");
        validateInput(requestBody);
        
        String decodedXML = convertToRawXML(requestBody);
        Map<String, String> attributes = extractSamlAttributes(decodedXML);
        
        logger.info("Successfully processed SAML response with {} attributes", attributes.size());
        return attributes.toString();
    }

    private void validateInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new SamlDecoderException("Input cannot be null or empty");
        }
    }

    private String convertToRawXML(String input) {
        try {
            JSONObject json = new JSONObject(input);
            String samlResponse = json.toMap().get("inputField").toString().trim();
            
            if (samlResponse.isEmpty()) {
                throw new SamlDecoderException("SAML response is empty");
            }
            
            return new String(base64Decoder.decode(samlResponse));
        } catch (Exception e) {
            logger.error("Failed to decode SAML response", e);
            throw new SamlDecoderException("Failed to decode SAML response", e);
        }
    }

    public Map<String, String> extractSamlAttributes(String xml) {
        validateInput(xml);
        
        try {
            Response samlResponse = parseSamlResponse(xml);
            return extractAttributesFromResponse(samlResponse);
        } catch (Exception e) {
            logger.error("Failed to process SAML attributes", e);
            throw new SamlDecoderException("Failed to process SAML attributes", e);
        }
    }

    private Response parseSamlResponse(String xml) throws Exception {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8))) {
            
            Document document = parserPool.parse(inputStream);
            Element element = document.getDocumentElement();
            
            ResponseUnmarshaller unmarshaller = (ResponseUnmarshaller) 
                unmarshallerFactory.getUnmarshaller(element);
            
            return (Response) unmarshaller.unmarshall(element);
        }
    }

    private Map<String, String> extractAttributesFromResponse(Response samlResponse) {
        Map<String, String> attributes = new HashMap<>();
        
        for (Assertion assertion : samlResponse.getAssertions()) {
            assertion.getAttributeStatements().forEach(attributeStatement -> 
                attributeStatement.getAttributes().forEach(attribute -> {
                    String name = attribute.getName();
                    String value = attribute.getAttributeValues().get(0).getDOM().getTextContent();
                    attributes.put(name, value);
                    logger.debug("Extracted SAML attribute: {}={}", name, value);
                })
            );
        }
        
        return attributes;
    }

    public String processHarFile(MultipartFile file) {
        logger.info("Processing HAR file: {}", file.getOriginalFilename());
        validateHarFile(file);
        
        String samlResponse = extractSamlResponseFromHar(file);
        Map<String, String> attributes = extractSamlAttributes(samlResponse);
        
        logger.info("Successfully processed HAR file with {} attributes", attributes.size());
        return attributes.toString();
    }

    private void validateHarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new SamlDecoderException("Please select a valid HAR file to upload");
        }
        if (!file.getOriginalFilename().toLowerCase().endsWith(".har")) {
            throw new SamlDecoderException("Uploaded file must be a .har file");
        }
    }

    private String extractSamlResponseFromHar(MultipartFile file) {
        try {
            File tempFile = createTempHarFile(file);
            Har har = new HarReader().readFromFile(tempFile);
            
            return har.getLog().getEntries().stream()
                .filter(this::isSamlLoginRequest)
                .filter(entry -> entry.getRequest().getPostData().getText()
                    .startsWith(SAML_RESPONSE_PREFIX))
                .map(entry -> decodeSamlResponse(entry.getRequest().getPostData().getText()))
                .findFirst()
                .orElseThrow(() -> new SamlDecoderException("No valid SAML response found in HAR file"));
                
        } catch (Exception e) {
            logger.error("Error processing HAR file", e);
            throw new SamlDecoderException("Failed to process HAR file", e);
        }
    }

    private File createTempHarFile(MultipartFile file) throws IOException {
        File tempFile = File.createTempFile("saml_", ".har");
        file.transferTo(tempFile);
        tempFile.deleteOnExit();
        return tempFile;
    }

    private boolean isSamlLoginRequest(HarEntry entry) {
        String url = entry.getRequest().getUrl();
        return (url.startsWith(UAT_LOGIN_URL) || url.startsWith(PROD_LOGIN_URL)) &&
               entry.getRequest().getMethod().equals(HttpMethod.POST);
    }

    private String decodeSamlResponse(String postData) {
        try {
            String samlResponse = postData.substring(SAML_RESPONSE_PREFIX.length());
            samlResponse = URLDecoder.decode(samlResponse, StandardCharsets.UTF_8.name());
            return new String(base64Decoder.decode(samlResponse));
        } catch (Exception e) {
            throw new SamlDecoderException("Failed to decode SAML response", e);
        }
    }
}


