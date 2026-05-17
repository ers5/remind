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
        STATUS status = decideStatus(usage, willPower);
        log.info("현재 상태: "+status);

        String prompt = String.format("""
{
  "current_state": {
    "app_name": "%s",
    "initial_status": "%s",
    "pre_input": "%s",
    "post_input": "%s"
  },
  "instruction": "사용자가 앱을 잠근 이유(pre_input)와 지금 다시 열려는 핑계(post_input)를 평가하여, 초기 상태(initial_status)를 조정하고 허용 시간과 메시지를 결정해라.

1. 상태 조정 규칙 (단계 방향: CRITICAL ← OVERUSE ← WARNING ← OPTIMAL)
      사용자 입력(post_input)의 사유를 평가하여 상태를 다음과 같이 이동해라.

      - [타당한 사유]: 학업, 업무, 긴급 사태 등 정당한 사유가 구체적인 경우
        -> 오른쪽(OPTIMAL 방향)으로 1단계 이동 (예: OVERUSE -> WARNING)
        -> 이미 'OPTIMAL' 상태라면 'OPTIMAL'을 그대로 유지

      - [단순 충동]: "그냥", "심심해서", "잠깐만" 등 단순 사용 욕구 및 핑계인 경우
        -> 왼쪽(CRITICAL 방향)으로 1단계 이동 (예: WARNING -> OVERUSE)
        -> 이미 'CRITICAL' 상태라면 더 내려갈 곳이 없으므로 반드시 'CRITICAL'을 그대로 유지 (절대로 OVERUSE로 올리지 마라)

      - [무의미/비협조적 입력]: 사유가 없거나, 욕설/초성/반발 등 대화를 거부하는 경우
        -> 현재 상태와 관계없이 무조건 최종 상태를 'CRITICAL'로 강제 고정

   2. 최종 조정된 상태 기준 허용 시간(allowedTime) 결정 규칙:
      - 최종 상태가 'OPTIMAL'이면: 30 이상 60 이하의 정수 중 선택
      - 최종 상태가 'WARNING'이면: 10 이상 30 이하의 정수 중 선택
      - 최종 상태가 'OVERUSE'이면: 5 이상 10 이하의 정수 중 선택
      - 최종 상태가 'CRITICAL'이면: 무조건 0으로 설정

   3. 메시지(text) 작성 규칙:
      - 말투는 반드시 '따뜻한 반말'로 작성해라.
      - 메시지에 이모티콘은 절대 넣지 마라.
      - 1~2줄로 짧게 작성해라.

   4. 출력 형식:
      - 결과는 아래 key를 포함한 오직 JSON 형식으로만 반환해라. status 값은 니가 최종 조정한 상태값으로 채워라.
      {
        "status": "최종 조정된 상태(OPTIMAL, WARNING, OVERUSE, CRITICAL 중 택1)",
        "allowedTime": (분 단위 정수),
        "text": "(생성된 메시지)"
      }"
}
""", dto.appName(), status.name(), dto.lockReason(), dto.userInput());

        for (int i = 0; i < MAXCOUNT; i++) {
            try {
                String result = chatModel.call(prompt);
                log.info("LLM(시도 {}): {}", i + 1, result);

                String json = extractJson(result);
                log.info("최종 반환: "+json);
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

    private STATUS decideStatus(String usage, String willPower) {
        log.info("판단 전: "+"usage: {}, willPower: {}", usage, willPower);
        if ("HIGH".equals(usage) && "LOW".equals(willPower)) return STATUS.CRITICAL;
        else if ("LOW".equals(usage) && "HIGH".equals(willPower)) return STATUS.OPTIMAL;
        else if ("HIGH".equals(usage) || "LOW".equals(willPower)) return STATUS.OVERUSE;
        return STATUS.WARNING;
    }
}