package teample.remind.sevice;

import org.springframework.stereotype.Service;
import teample.remind.DTO.UserRequestDTO;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class UserContextConvertService {
    public String convertToWillpowerLevel(Integer score) {
        if (score<=50) return "LOW";
        else if (score<=80) return "MID";
        else return "HIGH";
    }

    public String convertToUsageLevel(UserRequestDTO.CurrentStats stats) {
        Integer count = stats.todayOpenAppCount();
        Integer accum = stats.accumUseApp();

        if(count<=3 && accum<=30) return "LOW";
        else if(count>7 || accum>60) return "HIGH";
        else return "MID";
    }

    public Integer extractMinutes(String input) {
        if(input==null || input.isBlank()) return null;

        Pattern pattern = Pattern.compile("(\\d+)\\s*(시간|분|초)");
        Matcher matcher = pattern.matcher(input);

        int totalMinute=0;
        boolean found=false;

        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);

            switch (unit) {
                case "시간" -> totalMinute += value * 60;
                case "분" -> totalMinute += value;
                case "초" -> totalMinute += (int)Math.ceil(value / 60.0);
            }
            found=true;
        }
        if (!found) return null;
        return totalMinute;
    }
}
