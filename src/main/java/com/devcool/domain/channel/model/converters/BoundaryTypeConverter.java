package com.devcool.domain.channel.model.converters;

import com.devcool.domain.channel.model.enums.BoundaryType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

@Converter(autoApply = false)
public class BoundaryTypeConverter implements AttributeConverter<BoundaryType, Integer> {
  @Override
  public Integer convertToDatabaseColumn(BoundaryType boundaryType) {
    return Objects.isNull(boundaryType) ? null : boundaryType.getCode();
  }

  @Override
  public BoundaryType convertToEntityAttribute(Integer dbCode) {
    return BoundaryType.fromCode(dbCode);
  }
}
