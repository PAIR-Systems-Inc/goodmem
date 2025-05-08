package com.goodmem.rest.dto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Associates a REST Data Transfer Object (DTO) with its corresponding
 * Protobuf message class used in the gRPC service implementation.
 *
 * <p>This annotation can be used for:
 * <ul>
 * <li>Documenting the relationship between REST DTOs and Protobuf messages.</li>
 * <li>Potentially for tools or reflection-based logic that map between
 * REST and gRPC layers.</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME) // Or RetentionPolicy.SOURCE if only for documentation/compile-time processing
@Target(ElementType.TYPE)         // Indicates this annotation can be applied to classes/interfaces/enums/records
public @interface ProtobufEquivalent {

  /**
   * Specifies the corresponding Protobuf message class.
   *
   * @return The Class object of the Protobuf message.
   */
  Class<?> value(); // Using 'value()' allows for concise annotation usage, e.g., @ProtobufEquivalent(MyProto.class)

  /**
   * Optional: A brief description of the mapping or any nuances.
   *
   * @return A description string.
   */
  String description() default "";

}
