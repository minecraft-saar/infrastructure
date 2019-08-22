package de.saar.minecraft.architect;

import de.saar.minecraft.shared.StatusMessage;
import de.saar.minecraft.shared.TextMessage;
import io.grpc.stub.StreamObserver;

public interface Architect {
    public void handleStatusInformation(StatusMessage request, StreamObserver<TextMessage> responseObserver);
    public String getArchitectInformation();
}
