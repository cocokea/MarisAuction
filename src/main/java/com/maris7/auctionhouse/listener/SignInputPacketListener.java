package com.maris7.auctionhouse.listener;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUpdateSign;
import com.maris7.auctionhouse.service.SignInputService;
import org.bukkit.entity.Player;

public final class SignInputPacketListener extends SimplePacketListenerAbstract {

    private final SignInputService signInputService;

    public SignInputPacketListener(SignInputService signInputService) {
        super(PacketListenerPriority.NORMAL);
        this.signInputService = signInputService;
    }

    @Override
    public void onPacketPlayReceive(PacketPlayReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.UPDATE_SIGN) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player) || !signInputService.hasSession(player.getUniqueId())) {
            return;
        }
        WrapperPlayClientUpdateSign packet = new WrapperPlayClientUpdateSign(event);
        signInputService.handleSignResponse(player, packet.getBlockPosition(), packet.getTextLines());
    }
}
