/*
 *
 * Copyright (c) 2019, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.psiphon3;

import androidx.annotation.Nullable;

import com.psiphon3.psiphonlibrary.VpnAppsUtils;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Immutable tunnel state. Hand-written instead of @AutoValue: AutoValue's generated constructor
 * rejected null for the @Nullable connectionData() property at runtime ("Null connectionData"),
 * which crashed MainActivity on launch through TunnelState.unknown().
 */
public final class TunnelState {
    public enum Status {
        RUNNING,
        STOPPED,
        UNKNOWN,
    }

    public static final class ConnectionData {
        public enum NetworkConnectionState {
            CONNECTED,
            CONNECTING,
            WAITING_FOR_NETWORK,
        }

        private final NetworkConnectionState networkConnectionState;
        private final String clientRegion;
        private final String clientVersion;
        private final String propagationChannelId;
        private final String sponsorId;
        private final int httpPort;
        private final int socksPort;
        private final boolean isLanSharingEnabled;
        @Nullable
        private final ArrayList<String> homePages;
        private final VpnAppsUtils.VpnAppsExclusionSetting vpnMode;
        @Nullable
        private final ArrayList<String> vpnApps;

        private ConnectionData(Builder b) {
            this.networkConnectionState = b.networkConnectionState;
            this.clientRegion = b.clientRegion;
            this.clientVersion = b.clientVersion;
            this.propagationChannelId = b.propagationChannelId;
            this.sponsorId = b.sponsorId;
            this.httpPort = b.httpPort;
            this.socksPort = b.socksPort;
            this.isLanSharingEnabled = b.isLanSharingEnabled;
            this.homePages = b.homePages;
            this.vpnMode = b.vpnMode;
            this.vpnApps = b.vpnApps;
        }

        public NetworkConnectionState networkConnectionState() { return networkConnectionState; }
        public String clientRegion() { return clientRegion; }
        public String clientVersion() { return clientVersion; }
        public String propagationChannelId() { return propagationChannelId; }
        public String sponsorId() { return sponsorId; }
        public int httpPort() { return httpPort; }
        public int socksPort() { return socksPort; }
        public boolean isLanSharingEnabled() { return isLanSharingEnabled; }
        @Nullable
        public ArrayList<String> homePages() { return homePages; }
        public VpnAppsUtils.VpnAppsExclusionSetting vpnMode() { return vpnMode; }
        @Nullable
        public ArrayList<String> vpnApps() { return vpnApps; }

        public static Builder builder() {
            return new Builder();
        }

        public boolean isConnected() {
            return networkConnectionState == NetworkConnectionState.CONNECTED;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConnectionData)) return false;
            ConnectionData that = (ConnectionData) o;
            return httpPort == that.httpPort
                    && socksPort == that.socksPort
                    && isLanSharingEnabled == that.isLanSharingEnabled
                    && networkConnectionState == that.networkConnectionState
                    && Objects.equals(clientRegion, that.clientRegion)
                    && Objects.equals(clientVersion, that.clientVersion)
                    && Objects.equals(propagationChannelId, that.propagationChannelId)
                    && Objects.equals(sponsorId, that.sponsorId)
                    && Objects.equals(homePages, that.homePages)
                    && vpnMode == that.vpnMode
                    && Objects.equals(vpnApps, that.vpnApps);
        }

        @Override
        public int hashCode() {
            return Objects.hash(networkConnectionState, clientRegion, clientVersion,
                    propagationChannelId, sponsorId, httpPort, socksPort, isLanSharingEnabled,
                    homePages, vpnMode, vpnApps);
        }

        public static final class Builder {
            // Defaults match the previous AutoValue builder defaults.
            private NetworkConnectionState networkConnectionState = NetworkConnectionState.CONNECTING;
            private String clientRegion = "";
            private String clientVersion = "";
            private String propagationChannelId = "";
            private String sponsorId = "";
            private int httpPort = 0;
            private int socksPort = 0;
            private boolean isLanSharingEnabled = false;
            @Nullable
            private ArrayList<String> homePages = null;
            private VpnAppsUtils.VpnAppsExclusionSetting vpnMode = VpnAppsUtils.VpnAppsExclusionSetting.ALL_APPS;
            @Nullable
            private ArrayList<String> vpnApps = null;

            public Builder setNetworkConnectionState(NetworkConnectionState v) { networkConnectionState = v; return this; }
            public Builder setClientRegion(String v) { clientRegion = v; return this; }
            public Builder setClientVersion(String v) { clientVersion = v; return this; }
            public Builder setPropagationChannelId(String v) { propagationChannelId = v; return this; }
            public Builder setSponsorId(String v) { sponsorId = v; return this; }
            public Builder setHttpPort(int v) { httpPort = v; return this; }
            public Builder setSocksPort(int v) { socksPort = v; return this; }
            public Builder setIsLanSharingEnabled(boolean v) { isLanSharingEnabled = v; return this; }
            public Builder setHomePages(@Nullable ArrayList<String> v) { homePages = v; return this; }
            public Builder setVpnMode(VpnAppsUtils.VpnAppsExclusionSetting v) { vpnMode = v; return this; }
            public Builder setVpnApps(@Nullable ArrayList<String> v) { vpnApps = v; return this; }

            public ConnectionData build() {
                return new ConnectionData(this);
            }
        }
    }

    private final Status status;
    @Nullable
    private final ConnectionData connectionData;

    private TunnelState(Status status, @Nullable ConnectionData connectionData) {
        this.status = status;
        this.connectionData = connectionData;
    }

    public Status status() {
        return status;
    }

    @Nullable
    public ConnectionData connectionData() {
        return connectionData;
    }

    public static TunnelState unknown() {
        return new TunnelState(Status.UNKNOWN, null);
    }

    public static TunnelState stopped() {
        return new TunnelState(Status.STOPPED, null);
    }

    public static TunnelState running(ConnectionData connectionData) {
        return new TunnelState(Status.RUNNING, connectionData);
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    public boolean isUnknown() {
        return status == Status.UNKNOWN;
    }

    public boolean isStopped() {
        return status == Status.STOPPED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TunnelState)) return false;
        TunnelState that = (TunnelState) o;
        return status == that.status && Objects.equals(connectionData, that.connectionData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, connectionData);
    }
}
