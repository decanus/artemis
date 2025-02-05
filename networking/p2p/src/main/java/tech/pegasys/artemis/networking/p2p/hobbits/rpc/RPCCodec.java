/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.artemis.networking.p2p.hobbits.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Splitter;
import de.undercouch.bson4jackson.BsonFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.p2p.hobbits.Codec;
import tech.pegasys.artemis.util.json.BytesModule;

public final class RPCCodec implements Codec {

  static final ObjectMapper mapper =
      new ObjectMapper(new BsonFactory()).registerModule(new BytesModule());

  private static final AtomicLong counter = new AtomicLong(1);

  private static long nextRequestNumber() {
    long requestNumber = counter.getAndIncrement();
    if (requestNumber < 1) {
      counter.set(1);
      return 1;
    }
    return requestNumber;
  }

  private RPCCodec() {}

  /**
   * Gets the json object mapper
   *
   * @return
   */
  public static ObjectMapper getMapper() {
    return mapper;
  }

  /**
   * Creates an empty goodbye message.
   *
   * @return the encoded bytes of a goodbye message.
   */
  public static Bytes createGoodbye() {
    return encode(RPCMethod.GOODBYE, Collections.emptyMap(), null);
  }

  /**
   * Encodes a message into a RPC request
   *
   * @param methodId the RPC method
   * @param request the payload of the request
   * @param pendingResponses the set of pending responses code to update
   * @return the encoded RPC message
   */
  public static Bytes encode(
      RPCMethod methodId, Object request, @Nullable Set<Long> pendingResponses) {
    long requestNumber = nextRequestNumber();
    if (pendingResponses != null) {
      pendingResponses.add(requestNumber);
    }
    return encode(methodId, request, requestNumber);
  }

  /**
   * Encodes a message into a RPC request
   *
   * @param methodId the RPC method
   * @param request the payload of the request
   * @param requestNumber a request number
   * @return the encoded RPC message
   */
  public static Bytes encode(RPCMethod methodId, Object request, long requestNumber) {

    String requestLine = "EWP " + VERSION + " RPC ";
    ObjectNode headerNode = mapper.createObjectNode();
    headerNode.put("method_id", methodId.code());
    headerNode.put("id", requestNumber);
    ObjectNode bodyNode = mapper.createObjectNode();
    bodyNode.putPOJO("body", request);
    try {

      Bytes header = Bytes.wrap(mapper.writer().writeValueAsBytes(headerNode));
      Bytes body = Bytes.wrap(mapper.writer().writeValueAsBytes(bodyNode));
      requestLine += header.size();
      requestLine += " ";
      requestLine += body.size();
      requestLine += "\n";
      return Bytes.concatenate(
          Bytes.wrap(requestLine.getBytes(StandardCharsets.UTF_8)), header, body);
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Decodes a RPC message into a payload.
   *
   * @param message the bytes of the message to read
   * @return the payload, decoded
   */
  public static RPCMessage decode(Bytes message) {

    Bytes requestLineBytes = null;
    for (int i = 0; i < message.size(); i++) {
      if (message.get(i) == (byte) '\n') {
        requestLineBytes = message.slice(0, i + 1);
        break;
      }
    }
    if (requestLineBytes == null) {
      return null;
    }
    String requestLine = new String(requestLineBytes.toArrayUnsafe(), StandardCharsets.UTF_8);
    Iterator<String> segments = Splitter.on(" ").split(requestLine).iterator();
    String preamble = segments.next();
    String version = segments.next();
    String protocol = segments.next();
    int headerLength = Integer.parseInt(segments.next());
    int bodyLength = Integer.parseInt(segments.next().trim());

    if (message.size() < bodyLength + headerLength + requestLineBytes.size()) {
      return null;
    }

    try {
      byte[] header = message.slice(requestLineBytes.size(), headerLength).toArrayUnsafe();
      byte[] payload =
          message.slice(requestLineBytes.size() + headerLength, bodyLength).toArrayUnsafe();
      ObjectNode headerNode = (ObjectNode) mapper.readTree(header);
      long id = headerNode.get("id").longValue();
      int methodId = headerNode.get("method_id").intValue();
      ObjectNode bodyNode = (ObjectNode) mapper.readTree(payload);
      return new RPCMessage(
          id,
          RPCMethod.valueOf(methodId),
          bodyNode.get("body"),
          (bodyLength + requestLineBytes.size() + headerLength));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
