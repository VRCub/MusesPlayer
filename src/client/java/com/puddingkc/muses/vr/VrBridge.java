package com.puddingkc.muses.vr;

import org.joml.Matrix4f;
import org.vivecraft.api.client.VRRenderingAPI;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.VRState;

final class VrBridge {
    private static final ClientDataHolderVR DATA = ClientDataHolderVR.getInstance();
    private static final VRRenderingAPI API = VRRenderingAPI.instance();

    private VrBridge() {
    }

    static boolean rightEye() {
        return pass() == RenderPass.RIGHT;
    }

    static boolean active() {
        return VRState.VR_RUNNING;
    }

    static Matrix4f rotation() {
        return DATA.vrPlayer.getVRDataWorld().getEye(pass()).getMatrix();
    }

    private static RenderPass pass() {
        return API.getCurrentRenderPass();
    }
}
