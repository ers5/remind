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
    private final int MAXCOUNT=3;

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
        historyService.saveHistory(dto, result.status(), result.text(), result.allowedTime());
        log.info("DB 저장: "+result.status()+result.text());
        return result;
    }

    public UserResponseDTO judge(AiStatusJudgeResponse response) {
        String appName = response.appName();
        STATUS status = response.status();
        String reason = response.reason();
        Integer allowedTime = response.allowedTime();
        String allowedTimeText=allowedTime==null?"요청 시간 없음":allowedTime+"분";
        String prompt = String.format("""
        너는 사용자의 스마트폰 과사용을 막아주는 다정한 에이전트야.
        현재 상태(status)와 유저의 상황(reason)을 보고, 유저를 다독이거나 단호하게 제지하는 메시지를 1~2줄 친근한 반말 말투로 작성하고 허용 시간(allowedTime)을 결정해줘.
        
        [허용 시간 규칙]
        - CRITICAL 상태면 allowedTime은 무조건 0을 줘.
        - OVERUSE 상태면 5 ~ 10 사이를 줘.
        - WARNING 상태면 10 ~ 30 사이를 줘.
        - OPTIMAL 상태면 30 이상을 줘.
        
        [예시]
        상태: CRITICAL
        이유: 공부하다가 쉴려고 유튜브를 켬. 하지만 이미 과사용 상태임.
        요청시간: 10분
        출력: {"text": "지금 잠깐 쉬는 건 좋지만, 유튜브를 너무 오래 켜두면 집중력 깨질 거야. 지금은 폰 덮고 눈 감고 쉬자!", "allowedTime": 0}
        
        상태: OVERUSE
        이유: 릴스나 쇼츠 보려고 켰는데 이미 사용량이 많음.
        요청시간: 30분
        출력: {"text": "숏폼은 한 번 보면 시간 순삭인 거 알지? 딱 5분만 보고 진짜 끄는 거다? 약속해!", "allowedTime": 5}
        
        상태: WARNING
        이유: 카카오톡으로 친구들이랑 수다 떠는 중인데 슬슬 사용 시간이 길어짐.
        요청시간: 20분
        출력: {"text": "친구들이랑 대화하는 것도 좋지만 너무 오래 폰만 보면 피곤해. 딱 15분만 더 하고 마무리하자!", "allowedTime": 15}
        
        상태: OPTIMAL
        이유: 앱 사용량이 현저히 낮고 의지력도 좋음
        요청시간: 요청 시간 없음
        출력: {"text": "오늘 스마트폰 조절 아주 완벽해! 오늘 하루도 파이팅!", "allowedTime": 30}
        
        [실제 데이터]
        - 쓰려는 앱: %s
        - 상태: %s
        - 왜 이런상태인지: %s
        - 사용자가 요청한시간: %s
        
        응답은 반드시 JSON 형식으로만 출력해.
        """, appName, status, reason, allowedTimeText);

        String result = chatModel.call(prompt);
        log.info("LLM 반환: "+result);

        try{
            String json = extractJson(result);
            AiMessageResponse aiMessageResponse = objectMapper.readValue(json, AiMessageResponse.class);
            return new UserResponseDTO(status, aiMessageResponse.text(), aiMessageResponse.allowedTime());
        }catch (Exception e){
            log.error("응원메시지작성중 에러발생!");
            return new UserResponseDTO(STATUS.FAIL, "에러발생",0);
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
                
                [실제 데이터]
                앱: %s
                입력: %s
                의지력: %s
                앱 실행량: %s
                
                응답은 반드시 다른 말 없이 JSON 형식으로만 출력해.
                {"status":"OPTIMAL|WARNING|OVERUSE|CRITICAL","reason":"1~2문장"}
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

