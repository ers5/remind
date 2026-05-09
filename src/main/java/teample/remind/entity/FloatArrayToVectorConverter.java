package teample.remind.entity;

import jakarta.persistence.AttributeConverter;

import java.util.*;

public class FloatArrayToVectorConverter implements AttributeConverter<float[],String> {


    @Override
    public String convertToDatabaseColumn(float[] floats) {
        if (floats == null || floats.length == 0) return null;

        StringBuilder sb=new StringBuilder("[");
        for (int i = 0; i < floats.length; i++) {
            sb.append(floats[i]);
            if (i<floats.length-1)sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public float[] convertToEntityAttribute(String s) {
        if (s == null || s.isEmpty()) return null;

        String cleanString = s.substring(1, s.length() - 1);

        if (cleanString.isEmpty()) return new float[0];

        String[] parts = cleanString.split("\\s*,\\s*");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i]=Float.parseFloat(parts[i]);
        }

        return result;
    }
}
