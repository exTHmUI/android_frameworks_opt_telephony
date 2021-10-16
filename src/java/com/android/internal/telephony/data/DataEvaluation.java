/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.internal.telephony.data;

import android.annotation.CurrentTimeMillisLong;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.telephony.data.DataProfile;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The class to describe a data evaluation for whether allowing or disallowing
 * establishing a data network.
 */
public class DataEvaluation {
    /** For time stamp formatting in debug message use only. */
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    /** The reason for this evaluation */
    private final DataEvaluationReason mDataEvaluationReason;

    /** Data disallowed reasons. There could be multiple reasons for not allowing data. */
    private final @NonNull Set<DataDisallowedReason> mDataDisallowedReasons = new HashSet<>();

    /** Data allowed reason. It is intended to only have one allowed reason. */
    private DataAllowedReason mDataAllowedReason = DataAllowedReason.NONE;

    private @Nullable DataProfile mCandidateDataProfile = null;

    /** The timestamp of evaluation time */
    private @CurrentTimeMillisLong long mEvaluatedTime = 0;

    /**
     * Constructor
     *
     * @param reason The reason for this evaluation.
     */
    public DataEvaluation(DataEvaluationReason reason) {
        mDataEvaluationReason = reason;
    }

    /**
     * Add a data disallowed reason. Note that adding a disallowed reason will clean up the
     * allowed reason because they are mutual exclusive.
     *
     * @param reason Disallowed reason.
     */
    public void addDataDisallowedReason(DataDisallowedReason reason) {
        mDataAllowedReason = DataAllowedReason.NONE;
        mDataDisallowedReasons.add(reason);
        mEvaluatedTime = System.currentTimeMillis();
    }

    /**
     * Add a data allowed reason. Note that adding an allowed reason will clean up the disallowed
     * reasons because they are mutual exclusive.
     *
     * @param reason Allowed reason.
     */
    public void addDataAllowedReason(DataAllowedReason reason) {
        mDataDisallowedReasons.clear();

        // Only higher priority allowed reason can overwrite the old one. See
        // DataAllowedReason for the oder.
        if (reason.ordinal() > mDataAllowedReason.ordinal()) {
            mDataAllowedReason = reason;
        }
        mEvaluatedTime = System.currentTimeMillis();
    }

    /**
     * Set the candidate data profile for setup data network.
     *
     * @param dataProfile The candidate data profile.
     */
    public void setCandidateDataProfile(@NonNull DataProfile dataProfile) {
        mCandidateDataProfile = dataProfile;
    }

    /**
     * @return The candidate data profile for setup data network.
     */
    public @Nullable DataProfile getCandidateDataProfile() {
        return mCandidateDataProfile;
    }

    /**
     * @return {@code true} if data is allowed.
     */
    public boolean isDataAllowed() {
        return mDataDisallowedReasons.size() == 0;
    }

    /**
     * Check if it contains a certain disallowed reason.
     *
     * @param reason The disallowed reason to check.
     * @return {@code true} if the provided reason matches one of the disallowed reasons.
     */
    public boolean contains(DataDisallowedReason reason) {
        return mDataDisallowedReasons.contains(reason);
    }

    /**
     * Check if only one disallowed reason prevent data connection.
     *
     * @param reason The given reason to check
     * @return True if the given reason is the only one that prevents data connection
     */
    public boolean containsOnly(DataDisallowedReason reason) {
        return mDataDisallowedReasons.size() == 1 && contains(reason);
    }

    /**
     * Check if the allowed reason is the specified reason.
     *
     * @param reason The allowed reason.
     * @return {@code true} if the specified reason matches the allowed reason.
     */
    public boolean contains(DataAllowedReason reason) {
        return reason == mDataAllowedReason;
    }

    /**
     * @return {@code true} if the disallowed reasons contains hard reasons.
     */
    public boolean containsHardDisallowedReasons() {
        for (DataDisallowedReason reason : mDataDisallowedReasons) {
            if (reason.isHardReason()) {
                return true;
            }
        }
        return false;
    }

    /** The reason for evaluating unsatisfied network requests. */
    enum DataEvaluationReason {
        /** New request from the apps. */
        NEW_REQUEST,
        /** Data config changed. */
        DATA_CONFIG_CHANGED,
        /** SIM is loaded. */
        SIM_LOADED,
        /** Data profiles changed. */
        DATA_PROFILES_CHANGED,
        /** Airplane mode off. */
        AIRPLANE_MODE_OFF,
        /** When data RAT changes, some unsatisfied network requests might fit with the new RAT. */
        DATA_RAT_CHANGED,
        /** When service state changes from out of service to in service. */
        DATA_IN_SERVICE,
        /** When data is enabled (by user, carrier, thermal, etc...) */
        DATA_ENABLED,
        /** When data roaming is enabled. */
        ROAMING_ENABLED,
        /** When voice call ended (for concurrent voice/data not supported RAT). */
        VOICE_CALL_ENDED,
        /** When network no longer restricts mobile data. */
        DATA_RESTRICTED_LIFTED,
        /** Network capabilities changed. The unsatisfied requests might have chances to attach. */
        DATA_NETWORK_CAPABILITIES_CHANGED,
    }

    /** Disallowed reasons. There could be multiple reasons if data connection is not allowed. */
    public enum DataDisallowedReason {
        // Soft failure reasons. A soft reason means that in certain conditions, data is still
        // allowed. Normally those reasons are due to users settings.

        /** Data is disabled by the user or policy. */
        DATA_DISABLED(false),
        /** Data roaming is disabled by the user. */
        ROAMING_DISABLED(false),
        /** Default data not selected. */
        DEFAULT_DATA_UNSELECTED(false),

        // Belows are all hard failure reasons. A hard reason means no matter what the data should
        // not be allowed.
        /** Data registration state is not in service. */
        NOT_IN_SERVICE(true),
        /** Data config is not ready. */
        DATA_CONFIG_NOT_READY(true),
        /** SIM is not ready. */
        SIM_NOT_READY(true),
        /** Concurrent voice and data is not allowed. */
        CONCURRENT_VOICE_DATA_NOT_ALLOWED(true),
        /** Carrier notified data should be restricted. */
        DATA_RESTRICTED_BY_NETWORK(true),
        /** Radio power is off (i.e. airplane mode on) */
        RADIO_POWER_OFF(true),
        /** Data disabled by telephony in some scenarios, for example, emergency call. */
        INTERNAL_DATA_DISABLED(true),
        /** Airplane mode is forcibly turned on by the carrier. */
        RADIO_DISABLED_BY_CARRIER(true),
        /** Underlying data service is not bound. */
        DATA_SERVICE_NOT_READY(true);

        private final boolean mIsHardReason;

        /**
         * @return {@code true} if the disallowed reason is a hard reason.
         */
        public boolean isHardReason() {
            return mIsHardReason;
        }

        /**
         * Constructor
         *
         * @param isHardReason {@code true} if the disallowed reason is a hard reason. A hard reason
         * means no matter what the data should not be allowed. A soft reason means that in certain
         * conditions, data is still allowed.
         */
        DataDisallowedReason(boolean isHardReason) {
            mIsHardReason = isHardReason;
        }
    }

    /**
     * Data allowed reasons. There will be only one reason if data is allowed.
     */
    public enum DataAllowedReason {
        // Note that unlike disallowed reasons, we only have one allowed reason every time
        // when we check data is allowed or not. The order of these allowed reasons is very
        // important. The lower ones take precedence over the upper ones.
        /**
         * None. This is the initial value.
         */
        NONE,
        /**
         * The normal reason. This is the most common case.
         */
        NORMAL,
        /**
         * The network brought up by this network request is unmetered. Should allowed no matter
         * the user enables or disables data.
         */
        UNMETERED_USAGE,
        /**
         * The network request is restricted (i.e. Only privilege apps can access the network.)
         */
        RESTRICTED_REQUEST,
        /**
         * Data is allowed because the network request is for emergency. This should be always at
         * the bottom (i.e. highest priority)
         */
        EMERGENCY_REQUEST,
    }

    @Override
    public String toString() {
        StringBuilder evaluationStr = new StringBuilder();
        evaluationStr.append("Data evaluation: evaluation reason:" + mDataEvaluationReason + ", ");
        if (mDataDisallowedReasons.size() > 0) {
            evaluationStr.append("Data disallowed reasons:");
            for (DataDisallowedReason reason : mDataDisallowedReasons) {
                evaluationStr.append(" ").append(reason);
            }
        } else {
            evaluationStr.append("Data allowed reason:");
            evaluationStr.append(" ").append(mDataAllowedReason);
        }
        evaluationStr.append(", candidate profile=" + mCandidateDataProfile);
        evaluationStr.append(", time=" + TIME_FORMAT.format(mEvaluatedTime));
        return evaluationStr.toString();
    }

}