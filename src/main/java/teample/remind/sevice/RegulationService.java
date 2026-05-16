package teample.remind.sevice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import teample.remind.DTO.*;
import teample.remind.entity.UserHistory;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegulationService {

    private final HistoryService historyService;
    private final ChatModel chatModel;
    private final UserContextConvertService userContextConvertService;
    private final ObjectMapper objectMapper;
    private final int MAXCOUNT = 5;

    public UserResponseDTO evaluate(UserRequestDTO dto) {
        UserHistory history = historyService.getHistory(dto);

        if (history != null) {
            return new UserResponseDTO(history.getStatus(), history.getResponse(), history.getAllowedTime());
        }

        AiMessageResponse aiResponse = judge(dto);

        if (aiResponse.status() == STATUS.FAIL) {
            return new UserResponseDTO(STATUS.FAIL, "판단 중 문제가 생겼어. 잠시 후 다시 시도해줘.", 0);
        }

        historyService.saveHistory(dto, aiResponse.status(), aiResponse.text(), aiResponse.allowedTime());
        log.info("DB 저장: {} | {}", aiResponse.status(), aiResponse.text());

        return new UserResponseDTO(
                aiResponse.status(),
                aiResponse.text(),
                aiResponse.allowedTime()
        );
    }

    private AiMessageResponse judge(UserRequestDTO dto) {
        String usage = userContextConvertService.convertToUsageLevel(dto.currentStats());
        String willPower = userContextConvertService.convertToWillpowerLevel(dto.currentStats().willPowerScore());
        String prompt = String.format("""
{
  "current_state": {
    "app_name": "%s",
    "usage_level": "%s",
    "willpower_level": "%s",
    "pre_input": "%s",
    "post_input": "%s"
  },
  "instruction": "사용자의 현재 상태(current_state)와 핑계(post_input)를 평가하여 앱 사용 제한을 결정해라. 
   (text)의 말투는 반드시 '따뜻한 반말'로 작성해라. 
   결과는 상태(status: OPTIMAL, WARNING, OVERUSE, CRITICAL 중 택1), 
   허용할 시간(allowedTime, 분 단위 정수), 메시지(text)가 포함된 오직 JSON 형식으로만 반환해라.
   메시지에 이모지는 넣지마라, 메시지는 1~2줄로 짧게 작성해라
   "
}
""", dto.appName(), usage, willPower, dto.lockReason(), dto.userInput());

        for (int i = 0; i < MAXCOUNT; i++) {
            try {
                String result = chatModel.call(prompt);
                log.info("LLM(시도 {}): {}", i + 1, result);

                String json = extractJson(result);
                return objectMapper.readValue(json, AiMessageResponse.class);
            } catch (Exception e) {
                log.warn("LLM 응답 파싱 실패 (시도 {}/{}): {}", i + 1, MAXCOUNT, e.getMessage());
            }
        }

        return new AiMessageResponse(STATUS.FAIL, "판단 실패", 0);
    }

    private String extractJson(String result) {
        int start = result.indexOf("{");
        int end = result.lastIndexOf("}");

        if (start == -1 || end == -1 || start > end) {
            throw new IllegalArgumentException("JSON 객체가 없습니다: " + result);
        }

        return result.substring(start, end + 1);
    }
}