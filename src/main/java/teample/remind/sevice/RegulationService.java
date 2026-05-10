package teample.remind.sevice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
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
    private final int MAXCOUNT=5;

    public UserResponseDTO evaluate(UserRequestDTO dto) {
        UserHistory history = historyService.getHistory(dto);

        AiStatusJudgeResponse statusResponse;

        if (history != null) {
            statusResponse = new AiStatusJudgeResponse(
                    dto.appName(),
                    history.getStatus(),
                    history.getReason(),
                    userContextConvertService.extractMinutes(dto.userInput())
            );
            return new UserResponseDTO(history.getStatus(), history.getReason(), history.getAllowedTime());
        } else {
            statusResponse = judgeStatus(dto);
        }

        if (statusResponse.status() == STATUS.FAIL) {
            return new UserResponseDTO(
                    STATUS.FAIL,
                    "판단 중 문제가 생겼어. 잠시 후 다시 시도해줘.",
                    0
            );
        }
        UserResponseDTO result = judge(statusResponse);
        if (result.status() != STATUS.FAIL){
            historyService.saveHistory(dto, result.status(), result.text(), result.allowedTime());
            log.info("DB 저장: "+result.status()+result.text());
        }
        return result;
    }

    public UserResponseDTO judge(AiStatusJudgeResponse response) {
        String appName = response.appName();
        STATUS status = response.status();
        String reason = response.reason();
        Integer allowedTime = response.allowedTime();
        String allowedTimeText = (allowedTime == null || allowedTime == 0) ? "요청 시간 없음" : allowedTime + "분";

        String toneGuide = "";
        String timeGuide = "";
        String toneExample = "";

        switch (status) {
            case OPTIMAL -> {
                toneGuide = "사용자의 집중을 응원하고 격려해줘.";
                timeGuide = "사용자의 요청을 충분히 존중해서 30 이상의 값을 줘.";
                toneExample = "집중하는 모습이 정말 멋지다! 계획한 대로 조금만 더 힘내보자.";
            }
            case WARNING -> {
                toneGuide = "상황에 공감해주되, 슬슬 쉴 준비를 하자고 타일러.";
                timeGuide = "10에서 30 사이의 적절한 시간을 결정해.";
                toneExample = "조금만 즐기고 눈 건강을 위해 잠시 쉬는 건 어떨까?";
            }
            case OVERUSE -> {
                toneGuide = "충분히 즐겼음을 인지시켜주고, 이제는 정말 마무리할 시간이라고 설득해.";
                timeGuide = "5에서 10 사이의 최소한의 마무리 시간만 결정해.";
                toneExample = "너무 오래하면 힘들어질 거야. 이제 마무리하자.";
            }
            case CRITICAL -> {
                toneGuide = "사용자의 휴식을 걱정하며, 지금 바로 스마트폰을 내려놓자고 단호하게 권유해.";
                timeGuide = "무조건 0으로 결정해.";
                toneExample = "오늘 정말 고생 많았어. 이제는 폰을 내려놓고 너 자신에게 진정한 휴식을 줄 시간이야.";
            }
        }

        String prompt = String.format("""
    너는 사용자의 스마트폰 중독을 예방하는 에이전트야.
    
    [현재 데이터]
    - 사용 중인 앱: %s
    - 현재 상태: %s
    - 유저의 상황: %s
    - 유저가 요청한 시간: %s
    
    [지침]
    1. 말투: %s (다정한 반말)
    2. 시간: %s
    
    [메시지 톤 예시]
    - 예시 문구: "%s"
    
    [출력 형식]
    반드시 다른 설명 없이 아래 JSON 형식으로만 답해.
    {"text": "위의 지침과 예시를 참고한 메시지 내용 1~2줄로 작성해줘", "allowedTime": 지침에 따라 결정된 정수}
    """, appName, status, reason, allowedTimeText, toneGuide, timeGuide, toneExample);


        log.info("LLM에게 던진 프롬프트: \n" + prompt);
        String result = chatModel.call(prompt);
        log.info("LLM 반환: " + result);

        try {
            String json = extractJson(result);
            AiMessageResponse aiMessageResponse = objectMapper.readValue(json, AiMessageResponse.class);
            return new UserResponseDTO(status, aiMessageResponse.text(), aiMessageResponse.allowedTime());
        } catch (Exception e) {
            log.error("응원메시지작성중 에러발생!", e);
            return new UserResponseDTO(STATUS.FAIL, "에러발생", 0);
        }
    }




    public AiStatusJudgeResponse judgeStatus(UserRequestDTO dto) {
        String usage= userContextConvertService.convertToUsageLevel(dto.currentStats());
        String willPower= userContextConvertService.convertToWillpowerLevel(dto.currentStats().willPowerScore());
        Integer allowedTime=userContextConvertService.extractMinutes(dto.userInput());
        String appName=dto.appName();
        log.info("LLM 인풋:"+usage,willPower);
        String prompt = String.format("""
                너는 사용자의 앱 사용을 분석하여 현재 상태를 판단하는 조절기야.
                앱 실행량과 사용자의 입력을 바탕으로 기본 위험도를 잡고, 의지력(willPower)에 따라 최종 상태를 결정해줘.
                
                [상태 정의]
                - OPTIMAL: 건강한 사용, 과사용 위험 낮음
                - WARNING: 약한 과사용 징후, 주의 필요
                - OVERUSE: 과사용 가능성 높음, 제한 필요
                - CRITICAL: 과사용 상태 확정, 강한 제한 필요
                
                [상태 가감 로직]
                1. 기본적으로 앱 실행량이 HIGH면 위험, LOW면 안전으로 판단.
                2. 의지력이 LOW면 상태를 더 위험하게 올려 (예: OVERUSE -> CRITICAL).
                3. 의지력이 HIGH면 상태를 더 안전하게 내려 (예: OVERUSE -> WARNING).
                
                [예시]
                앱: YouTube
                입력: 너무 공부를 많이해서 머리가 아파, 잠깐 쉴래
                의지력: LOW
                앱 실행량: HIGH
                출력: {"status":"CRITICAL", "reason":"앱 실행량이 이미 높은데 의지력마저 낮아 통제 불능 상태로 강하게 의심돼."}
                
                앱: Instagram
                입력: 잠깐 머리 좀 식히게 10분만 할게
                의지력: HIGH
                앱 실행량: HIGH
                출력: {"status":"OVERUSE", "reason":"앱 실행량이 높아 위험하지만, 의지력이 높아 스스로 조절할 여지가 있어."}
                
                앱: 웹툰
                입력: 딱 한 편만 보고 잘거야
                의지력: MID
                앱 실행량: MID
                출력: {"status":"WARNING", "reason":"앱 실행량이 중간 정도이고 의지력도 평범해서 아직은 주의만 주면 되는 단계야."}
                
                앱: 노션
                입력: 내일 할 일 정리하려고
                의지력: HIGH
                앱 실행량: LOW
                출력: {"status":"OPTIMAL", "reason":"앱 실행량이 낮고 뚜렷한 목적이 있으며 의지력도 높아 완벽히 통제 중이야."}
                
                앱: 유튜브
                입력: 과제 확인 할 게 있어
                의지력: LOW
                앱 실행량: LOW
                출력: {"status":"CRITICAL", "reason":"유튜브로 과제를 확인한다는건 거짓말일 가능성이 높아 사용자의 진심이 걱정됨"}
                
                [실제 데이터]
                앱: %s
                입력: %s
                의지력: %s
                앱 실행량: %s
                
                응답은 반드시 다른 말 없이 JSON 형식으로만 출력해.
                {"status":"","reason":"1~2문장"}
                """, dto.appName(), dto.userInput(), willPower, usage);
        for (int i = 0; i < MAXCOUNT; i++) {
            String result = chatModel.call(prompt);
            log.info("LLM 반환: "+result);

            try {
                String json = extractJson(result);
                AiStatusJudgeResponse response = objectMapper.readValue(json, AiStatusJudgeResponse.class);

                STATUS status = response.status();
                return new AiStatusJudgeResponse(appName,status, response.reason(), allowedTime);
            } catch (Exception e) {
                log.warn("LLM 응답을 파싱 실패:{} ",i+1,e);
            }
        }
        return new AiStatusJudgeResponse("ERROR",STATUS.FAIL,"에러 발생",null);
    }

    private String extractJson(String result) {
        int start = result.indexOf("{");
        int end = result.indexOf("}");

        if (start == -1 || end == -1 || start > end) {
            throw new IllegalArgumentException("JSON 객체가 없습니다: " + result);
        }

        return result.substring(start, end + 1);
    }
    }

