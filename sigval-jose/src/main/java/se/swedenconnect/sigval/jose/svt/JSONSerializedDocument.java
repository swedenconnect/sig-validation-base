/*
 * Copyright (c) 2022.  Sweden Connect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package se.swedenconnect.sigval.jose.svt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonPropertyOrder({"payload", "signatures" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JSONSerializedDocument {

  private String payload;
  private List<JOSESignature> signatures;


  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonPropertyOrder({"protected","header", "signature" })
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class JOSESignature {

    @JsonProperty("protected")
    private String protectedHeader;

    @JsonProperty("header")
    private Map<String, Object> unprotectedHeader;

    private String signature;
  }

}