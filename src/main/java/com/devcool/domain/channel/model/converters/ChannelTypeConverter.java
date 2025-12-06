package com.devcool.domain.channel.model.converters;

import com.devcool.domain.channel.model.enums.ChannelType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Objects;

@Converter(autoApply = false)
public class ChannelTypeConverter implements AttributeConverter<ChannelType, Integer> {
  @Override
  public Integer convertToDatabaseColumn(ChannelType channelType) {
    return Objects.isNull(channelType) ? null : channelType.getCode();
  }

  @Override
  public ChannelType convertToEntityAttribute(Integer dbCode) {
    return ChannelType.fromCode(dbCode);
  }
}
