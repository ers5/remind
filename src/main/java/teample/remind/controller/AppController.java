package teample.remind.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import teample.remind.DTO.AiStatusJudgeResponse;
import teample.remind.DTO.STATUS;
import teample.remind.DTO.UserRequestDTO;
import teample.remind.DTO.UserResponseDTO;
import teample.remind.sevice.RegulationService;

import java.util.*;
@RestController
@RequiredArgsConstructor
public class AppController {

    private final RegulationService regulationService;

//    @PostMapping("/remind")
//    public ResponseEntity<UserResponseDTO> remind(@RequestBody UserRequestDTO dto) {
//        return ResponseEntity.ok(regulationService.getConsult(dto));
//    }

    @PostMapping("/test")
    public AiStatusJudgeResponse test(@RequestBody UserRequestDTO dto) {
        return regulationService.judgeStatus(dto);
    }


    @PostMapping("/testFinal")
    public UserResponseDTO test3(@RequestBody UserRequestDTO dto) {
        return regulationService.evaluate(dto);
    }
}
