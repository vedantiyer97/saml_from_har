package com.example.samldecoder;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;



@org.springframework.web.bind.annotation.RestController
public class RestController {
    private final RestImpl restImpl;

    public RestController(RestImpl restImpl) {
        this.restImpl = restImpl;
    }

    @PostMapping("/sendSaml")
    public String sendResponse(@RequestBody String Request) {
        return restImpl.response(Request);
    }

    @PostMapping("/sendFile")
    public String handleFileUpload(@RequestParam("file") MultipartFile file) {
        return restImpl.processHarFile(file);
    }

}
