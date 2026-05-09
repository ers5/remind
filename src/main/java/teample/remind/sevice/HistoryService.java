package teample.remind.sevice;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.stereotype.Service;
import teample.remind.DTO.STATUS;
import teample.remind.DTO.UserRequestDTO;
import teample.remind.entity.UserHistory;
import teample.remind.repository.UserHistoryRepository;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class HistoryService {

    private final UserHistoryRepository userHistoryRepository;
    private final OllamaEmbeddingModel embeddingModel;
    private final UserContextConvertService userContextConvertService;

    public UserHistory getHistory(UserRequestDTO dto) {

        String appName=dto.appName();
        String willPowerLevel = userContextConvertService.convertToWillpowerLevel(dto.currentStats().willPowerScore());
        String usageLevel = userContextConvertService.convertToUsageLevel(dto.currentStats());
        Integer requestMinute = userContextConvertService.extractMinutes(dto.userInput());
        float[] vector = embeddingModel.embed(dto.userInput());

        return userHistoryRepository.findCloserHistory(vector,appName,willPowerLevel,usageLevel,requestMinute)
                .filter(history->calculateCosineDistance(vector,history.getEmbedding())<0.1)
                .map(history->{
                    history.incrementCount();;
                    return userHistoryRepository.save(history);
                })
                .orElse(null);
    }

    private double calculateCosineDistance(float[] v1,float[] v2) {
        double dotProduct=0.0;
        double norm1=0.0;
        double norm2=0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct+=v1[i]*v2[i];
            norm1 += Math.pow(v1[i], 2);
            norm2 += Math.pow(v2[i], 2);
        }
        double similarity=dotProduct/(Math.sqrt(norm1)*Math.sqrt(norm2));
        return 1-similarity;

    }
    @Transactional
    public UserHistory saveHistory(UserRequestDTO dto, STATUS status, String reason,Integer allowedTime) {
        String willPowerLevel =
                userContextConvertService.convertToWillpowerLevel(dto.currentStats().willPowerScore());

        String usageLevel =
                userContextConvertService.convertToUsageLevel(dto.currentStats());

        float[] vector = embeddingModel.embed(dto.userInput());

        UserHistory history = new UserHistory(
                dto.appName(),
                dto.userInput(),
                willPowerLevel,
                usageLevel,
                status,
                reason,
                vector,
                userContextConvertService.extractMinutes(dto.userInput())
                , allowedTime
        );

        return userHistoryRepository.save(history);
    }


}
