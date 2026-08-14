package br.com.digitado.domain.converter;

import br.com.digitado.domain.enumeration.Dificuldade;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.text.Normalizer;

/**
 * Converte a coluna dificuldade (texto) no enum Dificuldade tolerando dados
 * importados fora do padrão: caixa ('dificil'), acentos ('difícil') e o feminino
 * 'media' (→ MEDIO). Um valor irreconhecível vira null - a palavra cai no
 * fallback do Palavra.getDificuldade() - em vez de derrubar a consulta inteira
 * com IllegalArgumentException (foi o que tirou a Palavra do Dia do ar).
 *
 * Na escrita o valor gravado é sempre o nome canônico do enum (FACIL/MEDIO/DIFICIL).
 */
@Converter
public class DificuldadeConverter implements AttributeConverter<Dificuldade, String> {

    @Override
    public String convertToDatabaseColumn(Dificuldade attribute) {
        return attribute != null ? attribute.name() : null;
    }

    @Override
    public Dificuldade convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        String norm = Normalizer.normalize(dbData.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .toUpperCase();
        return switch (norm) {
            case "FACIL" -> Dificuldade.FACIL;
            case "MEDIO", "MEDIA" -> Dificuldade.MEDIO;
            case "DIFICIL" -> Dificuldade.DIFICIL;
            default -> null;
        };
    }
}
