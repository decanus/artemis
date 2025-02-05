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

package tech.pegasys.artemis.datastructures.operations;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil.SSZTypes;
import tech.pegasys.artemis.util.hashtree.Merkleizable;

public class Deposit implements Merkleizable {

  private List<Bytes32> proof; // Bounded by DEPOSIT_CONTRACT_TREE_DEPTH
  private DepositData data;
  private UnsignedLong index;

  public Deposit(List<Bytes32> proof, DepositData data, UnsignedLong index) {
    this.proof = proof;
    this.data = data;
    this.index = index;
  }

  public Deposit(List<Bytes32> proof, DepositData data) {
    this.proof = proof;
    this.data = data;
  }

  public Deposit(DepositData data, UnsignedLong index) {
    this.data = data;
    this.index = index;
  }

  public static Deposit fromBytes(Bytes bytes) {
    return SSZ.decode(
        bytes,
        reader ->
            new Deposit(
                reader.readFixedBytesVector(Constants.DEPOSIT_CONTRACT_TREE_DEPTH, 32).stream()
                    .map(Bytes32::wrap)
                    .collect(Collectors.toList()),
                DepositData.fromBytes(reader.readBytes())));
  }

  public Bytes toBytes() {
    List<Bytes32> filledProofList = new ArrayList<>();
    filledProofList.addAll(proof);

    if (proof.size() < Constants.DEPOSIT_CONTRACT_TREE_DEPTH) {

      int elementsToFill = Constants.DEPOSIT_CONTRACT_TREE_DEPTH - proof.size();
      List<Bytes32> fillElements = Collections.nCopies(elementsToFill, Bytes32.ZERO);

      filledProofList.addAll(fillElements);
    }

    return SSZ.encode(
        writer -> {
          writer.writeFixedBytesVector(filledProofList);
          writer.writeBytes(data.toBytes());
        });
  }

  @Override
  public int hashCode() {
    return Objects.hash(proof, data);
  }

  @Override
  public boolean equals(Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Deposit)) {
      return false;
    }

    Deposit other = (Deposit) obj;
    return Objects.equals(this.getProof(), other.getProof())
        && Objects.equals(this.getData(), other.getData());
  }

  /** ******************* * GETTERS & SETTERS * * ******************* */
  public List<Bytes32> getProof() {
    return proof;
  }

  public void setProof(List<Bytes32> branch) {
    this.proof = branch;
  }

  public DepositData getData() {
    return data;
  }

  public void setData(DepositData data) {
    this.data = data;
  }

  @Override
  public Bytes32 hash_tree_root() {
    return HashTreeUtil.merkleize(
        Arrays.asList(
            // TODO Look at this - is this a TUPLE_OF_COMPOSITE
            HashTreeUtil.hash_tree_root(SSZTypes.BASIC, proof.toArray(new Bytes32[0])),
            data.hash_tree_root()));
  }

  public UnsignedLong getIndex() {
    return index;
  }
}
