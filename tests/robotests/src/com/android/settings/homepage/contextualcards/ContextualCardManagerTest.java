/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.homepage.contextualcards;

import static com.android.settings.homepage.contextualcards.slices.SliceContextualCardRenderer.VIEW_TYPE_DEFERRED_SETUP;
import static com.android.settings.homepage.contextualcards.slices.SliceContextualCardRenderer.VIEW_TYPE_FULL_WIDTH;
import static com.android.settings.homepage.contextualcards.slices.SliceContextualCardRenderer.VIEW_TYPE_HALF_WIDTH;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.util.ArrayMap;

import com.android.settings.homepage.contextualcards.conditional.ConditionFooterContextualCard;
import com.android.settings.homepage.contextualcards.conditional.ConditionHeaderContextualCard;
import com.android.settings.homepage.contextualcards.conditional.ConditionalContextualCard;
import com.android.settings.intelligence.ContextualCardProto;
import com.android.settings.slices.CustomSliceRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
public class ContextualCardManagerTest {

    private static final String TEST_SLICE_URI = "context://test/test";
    private static final String TEST_SLICE_NAME = "test_name";

    @Mock
    ContextualCardUpdateListener mListener;

    private Context mContext;
    private ContextualCardManager mManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        final ContextualCardsFragment fragment = new ContextualCardsFragment();
        mManager = new ContextualCardManager(mContext, fragment.getSettingsLifecycle(),
                null /* bundle */);
    }

    @Test
    public void sortCards_hasConditionalAndSliceCards_conditionalShouldAlwaysBeTheLast() {
        final List<ContextualCard> cards = new ArrayList<>();
        cards.add(new ConditionalContextualCard.Builder().build());
        cards.add(buildContextualCard(TEST_SLICE_URI));

        final List<ContextualCard> sortedCards = mManager.sortCards(cards);

        assertThat(sortedCards.get(cards.size() - 1).getCardType())
                .isEqualTo(ContextualCard.CardType.CONDITIONAL);
    }

    @Test
    public void onContextualCardUpdated_emptyMapWithExistingCards_shouldOnlyKeepConditionalCard() {
        mManager.mContextualCards.add(new ConditionalContextualCard.Builder().build());
        mManager.mContextualCards.add(
                buildContextualCard(TEST_SLICE_URI));
        mManager.setListener(mListener);

        //Simulate database returns no contents.
        mManager.onContextualCardUpdated(new ArrayMap<>());

        assertThat(mManager.mContextualCards).hasSize(1);
        assertThat(mManager.mContextualCards.get(0).getCardType())
                .isEqualTo(ContextualCard.CardType.CONDITIONAL);
    }

    @Test
    public void onContextualCardUpdated_hasEmptyMap_shouldKeepConditionalHeaderCard() {
        mManager.mContextualCards.add(new ConditionHeaderContextualCard.Builder().build());
        mManager.setListener(mListener);

        mManager.onContextualCardUpdated(new ArrayMap<>());

        assertThat(mManager.mContextualCards).hasSize(1);
        assertThat(mManager.mContextualCards.get(0).getCardType())
                .isEqualTo(ContextualCard.CardType.CONDITIONAL_HEADER);
    }

    @Test
    public void onContextualCardUpdated_hasEmptyMap_shouldKeepConditionalFooterCard() {
        mManager.mContextualCards.add(new ConditionFooterContextualCard.Builder().build());
        mManager.setListener(mListener);

        mManager.onContextualCardUpdated(new ArrayMap<>());

        assertThat(mManager.mContextualCards).hasSize(1);
        assertThat(mManager.mContextualCards.get(0).getCardType())
                .isEqualTo(ContextualCard.CardType.CONDITIONAL_FOOTER);
    }

    @Test
    public void getCardLoaderTimeout_noConfiguredTimeout_shouldReturnDefaultTimeout() {
        final long timeout = mManager.getCardLoaderTimeout(mContext);

        assertThat(timeout).isEqualTo(ContextualCardManager.CARD_CONTENT_LOADER_TIMEOUT_MS);
    }

    @Test
    public void getCardLoaderTimeout_hasConfiguredTimeout_shouldReturnConfiguredTimeout() {
        final long configuredTimeout = 5000L;
        Settings.Global.putLong(mContext.getContentResolver(),
                ContextualCardManager.KEY_GLOBAL_CARD_LOADER_TIMEOUT, configuredTimeout);

        final long timeout = mManager.getCardLoaderTimeout(mContext);

        assertThat(timeout).isEqualTo(configuredTimeout);
    }

    @Test
    public void onFinishCardLoading_fastLoad_shouldCallOnContextualCardUpdated() {
        mManager.mStartTime = System.currentTimeMillis();
        final ContextualCardManager manager = spy(mManager);
        doNothing().when(manager).onContextualCardUpdated(anyMap());

        manager.onFinishCardLoading(new ArrayList<>());
        verify(manager).onContextualCardUpdated(nullable(Map.class));
    }

    @Test
    public void onFinishCardLoading_slowLoad_shouldSkipOnContextualCardUpdated() {
        mManager.mStartTime = 0;
        final ContextualCardManager manager = spy(mManager);
        doNothing().when(manager).onContextualCardUpdated(anyMap());

        manager.onFinishCardLoading(new ArrayList<>());
        verify(manager, never()).onContextualCardUpdated(anyMap());
    }

    @Test
    public void onFinishCardLoading_newLaunch_twoLoadedCards_shouldShowTwoCards() {
        mManager.mStartTime = System.currentTimeMillis();
        mManager.setListener(mListener);
        final List<ContextualCard> cards = new ArrayList<>();
        cards.add(buildContextualCard(TEST_SLICE_URI));
        cards.add(buildContextualCard(TEST_SLICE_URI));

        mManager.onFinishCardLoading(cards);

        assertThat(mManager.mContextualCards).hasSize(2);
    }

    @Test
    public void onFinishCardLoading_hasSavedCard_shouldOnlyShowSavedCard() {
        mManager.setListener(mListener);
        final List<String> savedCardNames = new ArrayList<>();
        savedCardNames.add(TEST_SLICE_NAME);
        mManager.mIsFirstLaunch = false;
        mManager.mSavedCards = savedCardNames;
        final ContextualCard newCard =
                new ContextualCard.Builder()
                        .setName("test_name2")
                        .setCardType(ContextualCard.CardType.SLICE)
                        .setSliceUri(Uri.parse("content://test/test2"))
                        .build();
        final List<ContextualCard> loadedCards = new ArrayList<>();
        loadedCards.add(buildContextualCard(TEST_SLICE_URI));
        loadedCards.add(newCard);

        mManager.onFinishCardLoading(loadedCards);

        final List<String> actualCards = mManager.mContextualCards.stream()
                .map(ContextualCard::getName)
                .collect(Collectors.toList());
        final List<String> expectedCards = Arrays.asList(TEST_SLICE_NAME);
        assertThat(actualCards).containsExactlyElementsIn(expectedCards);
    }

    @Test
    public void onFinishCardLoading_reloadData_shouldOnlyShowOldCard() {
        mManager.setListener(mListener);
        mManager.mIsFirstLaunch = false;
        //old card
        mManager.mContextualCards.add(buildContextualCard(TEST_SLICE_URI));
        final ContextualCard newCard =
                new ContextualCard.Builder()
                        .setName("test_name2")
                        .setCardType(ContextualCard.CardType.SLICE)
                        .setSliceUri(Uri.parse("content://test/test2"))
                        .build();
        final List<ContextualCard> loadedCards = new ArrayList<>();
        loadedCards.add(buildContextualCard(TEST_SLICE_URI));
        loadedCards.add(newCard);

        mManager.onFinishCardLoading(loadedCards);

        final List<String> actualCards = mManager.mContextualCards.stream()
                .map(ContextualCard::getName)
                .collect(Collectors.toList());
        final List<String> expectedCards = Arrays.asList(TEST_SLICE_NAME);
        assertThat(actualCards).containsExactlyElementsIn(expectedCards);
    }


    @Test
    public void getCardsWithViewType_noSuggestionCards_shouldNotHaveHalfCards() {
        final List<Integer> categories = Arrays.asList(
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE
        );
        final List<ContextualCard> noSuggestionCards = buildCategoriedCards(getContextualCardList(),
                categories);

        final List<ContextualCard> result = mManager.getCardsWithViewType(noSuggestionCards);

        assertThat(result).hasSize(5);
        for (ContextualCard card : result) {
            assertThat(card.getViewType()).isEqualTo(VIEW_TYPE_FULL_WIDTH);
        }
    }

    @Test
    public void getCardsWithViewType_oneSuggestionCards_shouldNotHaveHalfCards() {
        final List<Integer> categories = Arrays.asList(
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE
        );
        final List<ContextualCard> oneSuggestionCards = buildCategoriedCards(
                getContextualCardList(), categories);

        final List<ContextualCard> result = mManager.getCardsWithViewType(oneSuggestionCards);

        assertThat(result).hasSize(5);
        for (ContextualCard card : result) {
            assertThat(card.getViewType()).isEqualTo(VIEW_TYPE_FULL_WIDTH);
        }
    }

    @Test
    public void getCardsWithViewType_twoConsecutiveSuggestionCards_shouldHaveTwoHalfCards() {
        final List<Integer> categories = Arrays.asList(
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE
        );
        final List<ContextualCard> twoConsecutiveSuggestionCards = buildCategoriedCards(
                getContextualCardList(), categories);
        final List<Integer> expectedValues = Arrays.asList(VIEW_TYPE_FULL_WIDTH,
                VIEW_TYPE_FULL_WIDTH, VIEW_TYPE_HALF_WIDTH, VIEW_TYPE_HALF_WIDTH,
                VIEW_TYPE_FULL_WIDTH);

        final List<ContextualCard> result = mManager.getCardsWithViewType(
                twoConsecutiveSuggestionCards);

        assertThat(result).hasSize(5);
        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).getViewType()).isEqualTo(expectedValues.get(i));
        }
    }

    @Test
    public void getCardsWithViewType_twoNonConsecutiveSuggestionCards_shouldNotHaveHalfCards() {
        final List<Integer> categories = Arrays.asList(
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE
        );
        final List<ContextualCard> twoNonConsecutiveSuggestionCards = buildCategoriedCards(
                getContextualCardList(), categories);

        final List<ContextualCard> result = mManager.getCardsWithViewType(
                twoNonConsecutiveSuggestionCards);

        assertThat(result).hasSize(5);
        for (ContextualCard card : result) {
            assertThat(card.getViewType()).isEqualTo(VIEW_TYPE_FULL_WIDTH);
        }
    }

    @Test
    public void getCardsWithViewType_threeConsecutiveSuggestionCards_shouldHaveTwoHalfCards() {
        final List<Integer> categories = Arrays.asList(
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE
        );
        final List<ContextualCard> threeConsecutiveSuggestionCards = buildCategoriedCards(
                getContextualCardList(), categories);
        final List<Integer> expectedValues = Arrays.asList(VIEW_TYPE_FULL_WIDTH,
                VIEW_TYPE_HALF_WIDTH, VIEW_TYPE_HALF_WIDTH, VIEW_TYPE_FULL_WIDTH,
                VIEW_TYPE_FULL_WIDTH);

        final List<ContextualCard> result = mManager.getCardsWithViewType(
                threeConsecutiveSuggestionCards);

        assertThat(result).hasSize(5);
        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).getViewType()).isEqualTo(expectedValues.get(i));
        }
    }

    @Test
    public void getCardsWithViewType_fourConsecutiveSuggestionCards_shouldHaveFourHalfCards() {
        final List<Integer> categories = Arrays.asList(
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE
        );
        final List<ContextualCard> fourConsecutiveSuggestionCards = buildCategoriedCards(
                getContextualCardList(), categories);
        final List<Integer> expectedValues = Arrays.asList(VIEW_TYPE_FULL_WIDTH,
                VIEW_TYPE_HALF_WIDTH, VIEW_TYPE_HALF_WIDTH, VIEW_TYPE_HALF_WIDTH,
                VIEW_TYPE_HALF_WIDTH);

        final List<ContextualCard> result = mManager.getCardsWithViewType(
                fourConsecutiveSuggestionCards);

        assertThat(result).hasSize(5);
        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).getViewType()).isEqualTo(expectedValues.get(i));
        }
    }

    @Test
    public void getCardsWithViewType_onlyDeferredSetupCard_shouldHaveDeferredSetupCard() {
        final List<ContextualCard> oneDeferredSetupCards = getDeferredSetupCardList();

        final List<ContextualCard> result = mManager.getCardsWithViewType(oneDeferredSetupCards);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getViewType()).isEqualTo(VIEW_TYPE_DEFERRED_SETUP);
    }

    @Test
    public void getCardsWithViewType_hasDeferredSetupCard_shouldHaveDeferredSetupCard() {
        final List<Integer> categories = Arrays.asList(
                ContextualCardProto.ContextualCard.Category.DEFERRED_SETUP_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE
        );
        final List<ContextualCard> cards = buildCategoriedCards(getContextualCardList(),
                categories);

        final List<ContextualCard> result = mManager.getCardsWithViewType(cards);

        assertThat(result).hasSize(5);
        assertThat(result.get(0).getViewType()).isEqualTo(VIEW_TYPE_DEFERRED_SETUP);
    }

    @Test
    public void getCardsWithViewType_noDeferredSetupCard_shouldNotHaveDeferredSetupCard() {
        final List<Integer> categories = Arrays.asList(
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.IMPORTANT_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE,
                ContextualCardProto.ContextualCard.Category.SUGGESTION_VALUE
        );
        final List<ContextualCard> cards = buildCategoriedCards(
                getContextualCardList(), categories);

        final List<ContextualCard> result = mManager.getCardsWithViewType(cards);

        assertThat(result).hasSize(5);
        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).getViewType()).isNotEqualTo(
                    ContextualCardProto.ContextualCard.Category.DEFERRED_SETUP_VALUE);
        }
    }

    private ContextualCard buildContextualCard(String sliceUri) {
        return new ContextualCard.Builder()
                .setName(TEST_SLICE_NAME)
                .setCardType(ContextualCard.CardType.SLICE)
                .setSliceUri(Uri.parse(sliceUri))
                .setViewType(VIEW_TYPE_FULL_WIDTH)
                .build();
    }

    private List<ContextualCard> buildCategoriedCards(List<ContextualCard> cards,
            List<Integer> categories) {
        final List<ContextualCard> result = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            result.add(cards.get(i).mutate().setCategory(categories.get(i)).build());
        }
        return result;
    }

    private List<ContextualCard> getContextualCardList() {
        final List<ContextualCard> cards = new ArrayList<>();
        cards.add(new ContextualCard.Builder()
                .setName("test_wifi")
                .setCardType(ContextualCard.CardType.SLICE)
                .setSliceUri(CustomSliceRegistry.CONTEXTUAL_WIFI_SLICE_URI)
                .setViewType(VIEW_TYPE_FULL_WIDTH)
                .build());
        cards.add(new ContextualCard.Builder()
                .setName("test_flashlight")
                .setCardType(ContextualCard.CardType.SLICE)
                .setSliceUri(
                        Uri.parse("content://com.android.settings.test.slices/action/flashlight"))
                .setViewType(VIEW_TYPE_FULL_WIDTH)
                .build());
        cards.add(new ContextualCard.Builder()
                .setName("test_connected")
                .setCardType(ContextualCard.CardType.SLICE)
                .setSliceUri(CustomSliceRegistry.BLUETOOTH_DEVICES_SLICE_URI)
                .setViewType(VIEW_TYPE_FULL_WIDTH)
                .build());
        cards.add(new ContextualCard.Builder()
                .setName("test_gesture")
                .setCardType(ContextualCard.CardType.SLICE)
                .setSliceUri(Uri.parse(
                        "content://com.android.settings.test.slices/action/gesture_pick_up"))
                .setViewType(VIEW_TYPE_FULL_WIDTH)
                .build());
        cards.add(new ContextualCard.Builder()
                .setName("test_battery")
                .setCardType(ContextualCard.CardType.SLICE)
                .setSliceUri(CustomSliceRegistry.BATTERY_INFO_SLICE_URI)
                .setViewType(VIEW_TYPE_FULL_WIDTH)
                .build());
        return cards;
    }

    private List<ContextualCard> getDeferredSetupCardList() {
        final List<ContextualCard> cards = new ArrayList<>();
        cards.add(new ContextualCard.Builder()
                .setName("deferred_setup")
                .setCardType(ContextualCard.CardType.SLICE)
                .setCategory(ContextualCardProto.ContextualCard.Category.DEFERRED_SETUP_VALUE)
                .setSliceUri(new Uri.Builder().appendPath("test_deferred_setup_path").build())
                .setViewType(VIEW_TYPE_FULL_WIDTH)
                .build());
        return cards;
    }
}
