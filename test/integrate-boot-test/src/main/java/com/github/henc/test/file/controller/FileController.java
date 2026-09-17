package com.github.henc.test.file.controller;

import com.github.henc.integrateboot.base.ResultInfo;
import com.github.henc.integrateboot.oss.OssObject;
import com.github.henc.integrateboot.oss.OssSaveRequest;
import com.github.henc.integrateboot.oss.OssStorage;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * File REST controller of the sample app: upload through the {@link OssStorage} facade,
 * download streamed straight to the response, metadata lookup and delete. Missing ids
 * need no handling here — the global exception handler renders the storage's
 * {@code OssObjectNotFoundException} as a ResultInfo 404.
 */
@RestController
@RequestMapping("/files")
public class FileController {

    private final OssStorage ossStorage;

    public FileController(OssStorage ossStorage) {
        this.ossStorage = ossStorage;
    }

    @PostMapping
    public ResultInfo upload(@RequestParam("file") MultipartFile file) throws IOException {
        OssObject stored = ossStorage.save(OssSaveRequest.of(file.getOriginalFilename()),
                file.getInputStream());
        return ResultInfo.success()
                .put("id", stored.id())
                .put("name", stored.name())
                .put("contentLength", stored.contentLength())
                .put("checksum", stored.checksum());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String id) {
        OssObject object = ossStorage.getObject(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(object.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(object.name(), StandardCharsets.UTF_8).build());
        headers.setContentLength(object.contentLength());
        return ResponseEntity.ok().headers(headers)
                .body(new InputStreamResource(ossStorage.openStream(id)));
    }

    @GetMapping("/{id}/metadata")
    public ResultInfo metadata(@PathVariable String id) {
        return ResultInfo.success("object", ossStorage.getObject(id));
    }

    @DeleteMapping("/{id}")
    public ResultInfo delete(@PathVariable String id) {
        ossStorage.delete(id);
        return ResultInfo.success();
    }
}
