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

package tech.pegasys.artemis.datastructures.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.newDeposits;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomUnsignedLong;
import static tech.pegasys.artemis.datastructures.util.DataStructureUtil.randomValidator;
import static tech.pegasys.artemis.util.hashtree.HashTreeUtil.is_power_of_two;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.datastructures.state.CrosslinkCommittee;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSVerify;

@ExtendWith(BouncyCastleExtension.class)
class BeaconStateUtilTest {
  @Test
  void minReturnsMin() {
    UnsignedLong actual = BeaconStateUtil.min(UnsignedLong.valueOf(13L), UnsignedLong.valueOf(12L));
    UnsignedLong expected = UnsignedLong.valueOf(12L);
    assertEquals(expected, actual);
  }

  @Test
  void minReturnsMinWhenEqual() {
    UnsignedLong actual = BeaconStateUtil.min(UnsignedLong.valueOf(12L), UnsignedLong.valueOf(12L));
    UnsignedLong expected = UnsignedLong.valueOf(12L);
    assertEquals(expected, actual);
  }

  @Test
  void maxReturnsMax() {
    UnsignedLong actual = BeaconStateUtil.max(UnsignedLong.valueOf(13L), UnsignedLong.valueOf(12L));
    UnsignedLong expected = UnsignedLong.valueOf(13L);
    assertEquals(expected, actual);
  }

  @Test
  void maxReturnsMaxWhenEqual() {
    UnsignedLong actual = BeaconStateUtil.max(UnsignedLong.valueOf(13L), UnsignedLong.valueOf(13L));
    UnsignedLong expected = UnsignedLong.valueOf(13L);
    assertEquals(expected, actual);
  }

  @Test
  void sqrtOfSquareNumber() {
    UnsignedLong actual = BeaconStateUtil.integer_squareroot(UnsignedLong.valueOf(3481L));
    UnsignedLong expected = UnsignedLong.valueOf(59L);
    assertEquals(expected, actual);
  }

  @Test
  void sqrtOfANonSquareNumber() {
    UnsignedLong actual = BeaconStateUtil.integer_squareroot(UnsignedLong.valueOf(27L));
    UnsignedLong expected = UnsignedLong.valueOf(5L);
    assertEquals(expected, actual);
  }

  @Test
  void sqrtOfANegativeNumber() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BeaconStateUtil.integer_squareroot(UnsignedLong.valueOf(-1L)));
  }

  @Test
  void validateProofOfPosessionReturnsTrueIfTheBLSSignatureIsValidForGivenDepositInputData() {
    Deposit deposit = newDeposits(1).get(0);
    BLSPublicKey pubkey = deposit.getData().getPubkey();
    DepositData depositData = deposit.getData();
    int domain =
        BeaconStateUtil.get_domain(
            createBeaconState(),
            Constants.DOMAIN_DEPOSIT,
            UnsignedLong.fromLongBits(Constants.GENESIS_EPOCH));

    assertTrue(
        BLSVerify.bls_verify(
            pubkey, depositData.signing_root("signature"), depositData.getSignature(), domain));
  }

  @Test
  void validateProofOfPosessionReturnsFalseIfTheBLSSignatureIsNotValidForGivenDepositInputData() {
    Deposit deposit = newDeposits(1).get(0);
    BLSPublicKey pubkey = BLSPublicKey.random();
    DepositData depositData = deposit.getData();
    int domain =
        BeaconStateUtil.get_domain(
            createBeaconState(),
            Constants.DOMAIN_DEPOSIT,
            UnsignedLong.fromLongBits(Constants.GENESIS_EPOCH));

    assertFalse(
        BLSVerify.bls_verify(
            pubkey, depositData.signing_root("signature"), depositData.getSignature(), domain));
  }

  @Test
  void getTotalBalanceAddsAndReturnsEffectiveTotalBalancesCorrectly() {
    // Data Setup
    BeaconState state = createBeaconState();
    CrosslinkCommittee crosslinkCommittee =
        new CrosslinkCommittee(UnsignedLong.ONE, Arrays.asList(0, 1, 2));

    // Calculate Expected Results
    UnsignedLong expectedBalance = UnsignedLong.ZERO;
    for (UnsignedLong balance : state.getBalances()) {
      if (balance.compareTo(UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE)) < 0) {
        expectedBalance = expectedBalance.plus(balance);
      } else {
        expectedBalance =
            expectedBalance.plus(UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE));
      }
    }

    UnsignedLong totalBalance =
        BeaconStateUtil.get_total_balance(state, crosslinkCommittee.getCommittee());
    assertEquals(expectedBalance, totalBalance);
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlot1() {
    BeaconState beaconState = createBeaconState();
    beaconState.setSlot(UnsignedLong.valueOf(Constants.GENESIS_SLOT));
    assertEquals(
        UnsignedLong.valueOf(Constants.GENESIS_EPOCH),
        BeaconStateUtil.get_previous_epoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlot2() {
    BeaconState beaconState = createBeaconState();
    beaconState.setSlot(UnsignedLong.valueOf(Constants.GENESIS_SLOT + Constants.SLOTS_PER_EPOCH));
    assertEquals(
        UnsignedLong.valueOf(Constants.GENESIS_EPOCH),
        BeaconStateUtil.get_previous_epoch(beaconState));
  }

  @Test
  void succeedsWhenGetPreviousSlotReturnsGenesisSlotPlusOne() {
    BeaconState beaconState = createBeaconState();
    beaconState.setSlot(
        UnsignedLong.valueOf(Constants.GENESIS_SLOT + 2 * Constants.SLOTS_PER_EPOCH));
    assertEquals(
        UnsignedLong.valueOf(Constants.GENESIS_EPOCH + 1),
        BeaconStateUtil.get_previous_epoch(beaconState));
  }

  @Test
  void succeedsWhenGetNextEpochReturnsTheEpochPlusOne() {
    BeaconState beaconState = createBeaconState();
    beaconState.setSlot(UnsignedLong.valueOf(Constants.GENESIS_SLOT));
    assertEquals(
        UnsignedLong.valueOf(Constants.GENESIS_EPOCH + 1),
        BeaconStateUtil.get_next_epoch(beaconState));
  }

  @Test
  void intToBytes() {
    long value = 0x0123456789abcdefL;
    assertEquals(Bytes.EMPTY, BeaconStateUtil.int_to_bytes(value, 0));
    assertEquals(Bytes.fromHexString("0xef"), BeaconStateUtil.int_to_bytes(value, 1));
    assertEquals(Bytes.fromHexString("0xefcd"), BeaconStateUtil.int_to_bytes(value, 2));
    assertEquals(Bytes.fromHexString("0xefcdab89"), BeaconStateUtil.int_to_bytes(value, 4));
    assertEquals(Bytes.fromHexString("0xefcdab8967452301"), BeaconStateUtil.int_to_bytes(value, 8));
    assertEquals(
        Bytes.fromHexString("0xefcdab89674523010000000000000000"),
        BeaconStateUtil.int_to_bytes(value, 16));
    assertEquals(
        Bytes.fromHexString("0xefcdab8967452301000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.int_to_bytes(value, 32));
  }

  @Test
  void intToBytes32Long() {
    assertEquals(
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.int_to_bytes32(0L));
    assertEquals(
        Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.int_to_bytes32(1L));
    assertEquals(
        Bytes32.fromHexString("0xffffffffffffffff000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.int_to_bytes32(-1L));
    assertEquals(
        Bytes32.fromHexString("0xefcdab8967452301000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.int_to_bytes32(0x0123456789abcdefL));
  }

  @Test
  void intToBytes32UnsignedLong() {
    assertEquals(
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.int_to_bytes32(UnsignedLong.ZERO));
    assertEquals(
        Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.int_to_bytes32(UnsignedLong.ONE));
    assertEquals(
        Bytes32.fromHexString("0xffffffffffffffff000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.int_to_bytes32(UnsignedLong.MAX_VALUE));
    assertEquals(
        Bytes32.fromHexString("0xefcdab8967452301000000000000000000000000000000000000000000000000"),
        BeaconStateUtil.int_to_bytes32(UnsignedLong.valueOf(0x0123456789abcdefL)));
  }

  @Test
  void bytesToInt() {
    assertEquals(0L, BeaconStateUtil.bytes_to_int(Bytes.fromHexString("0x00")));
    assertEquals(1L, BeaconStateUtil.bytes_to_int(Bytes.fromHexString("0x01")));
    assertEquals(1L, BeaconStateUtil.bytes_to_int(Bytes.fromHexString("0x0100000000000000")));
    assertEquals(
        0x123456789abcdef0L,
        BeaconStateUtil.bytes_to_int(Bytes.fromHexString("0xf0debc9a78563412")));
  }

  void isPowerOfTwo() {
    // Not powers of two:
    assertThat(is_power_of_two(UnsignedLong.ZERO)).isEqualTo(false);
    assertThat(is_power_of_two(UnsignedLong.valueOf(42L))).isEqualTo(false);
    assertThat(is_power_of_two(UnsignedLong.valueOf(Long.MAX_VALUE))).isEqualTo(false);
    // Powers of two:
    assertThat(is_power_of_two(UnsignedLong.ONE)).isEqualTo(true);
    assertThat(is_power_of_two(UnsignedLong.ONE.plus(UnsignedLong.ONE))).isEqualTo(true);
    assertThat(is_power_of_two(UnsignedLong.valueOf(0x040000L))).isEqualTo(true);
    assertThat(is_power_of_two(UnsignedLong.valueOf(0x0100000000L))).isEqualTo(true);
    assertThat(is_power_of_two(UnsignedLong.fromLongBits(0x8000000000000000L))).isEqualTo(true);
  }

  private BeaconState createBeaconState() {
    return createBeaconState(false, null, null);
  }

  private BeaconState createBeaconState(UnsignedLong amount, Validator knownValidator) {
    return createBeaconState(true, amount, knownValidator);
  }

  private BeaconState createBeaconState(
      boolean addToList, UnsignedLong amount, Validator knownValidator) {
    BeaconState beaconState = new BeaconStateWithCache();
    beaconState.setSlot(randomUnsignedLong());
    beaconState.setFork(
        new Fork(
            Bytes.ofUnsignedInt(Constants.GENESIS_FORK_VERSION),
            Bytes.ofUnsignedInt(Constants.GENESIS_FORK_VERSION),
            UnsignedLong.valueOf(Constants.GENESIS_EPOCH)));

    List<Validator> validatorList =
        new ArrayList<>(Arrays.asList(randomValidator(), randomValidator(), randomValidator()));
    List<UnsignedLong> balanceList =
        new ArrayList<>(
            Collections.nCopies(3, UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE)));

    if (addToList) {
      validatorList.add(knownValidator);
      balanceList.add(amount);
    }

    beaconState.setValidator_registry(validatorList);
    beaconState.setBalances(balanceList);
    return beaconState;
  }

  // *************** START Shuffling Tests ***************

  // TODO: tests for get_shuffling() - the reference tests are out of date.

  // The following are just sanity checks. The real testing is against the official test vectors,
  // elsewhere.

  @Test
  void succeedsWhenGetPermutedIndexReturnsAPermutation() {
    Bytes32 seed = Bytes32.random();
    int listSize = 1000;
    boolean[] done = new boolean[listSize]; // Initialised to false
    for (int i = 0; i < listSize; i++) {
      int idx = CrosslinkCommitteeUtil.get_shuffled_index(i, listSize, seed);
      assertFalse(done[idx]);
      done[idx] = true;
    }
  }

  @Test
  void succeedsWhenGetPermutedIndexAndShuffleGiveTheSameResults() {
    Bytes32 seed = Bytes32.random();
    int listSize = 1 + (int) randomUnsignedLong().longValue() % 1000;
    int[] shuffling = BeaconStateUtil.shuffle(listSize, seed);
    for (int i = 0; i < listSize; i++) {
      int idx = CrosslinkCommitteeUtil.get_shuffled_index(i, listSize, seed);
      assertEquals(shuffling[i], idx);
    }
  }

  private Validator createValidator() {
    List<Deposit> deposits = newDeposits(1);
    Deposit deposit = deposits.get(0);
    DepositData depositInput = deposit.getData();
    BLSPublicKey pubkey = depositInput.getPubkey();
    Bytes32 withdrawalCredentials = depositInput.getWithdrawal_credentials();
    UnsignedLong amount = deposit.getData().getAmount();

    return new Validator(
        pubkey,
        withdrawalCredentials,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        Constants.FAR_FUTURE_EPOCH,
        false,
        UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE));
  }

  // *************** END Shuffling Tests *****************
}
