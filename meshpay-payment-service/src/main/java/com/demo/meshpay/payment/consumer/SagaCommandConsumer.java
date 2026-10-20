package com.demo.meshpay.payment.consumer;

import com.demo.meshpay.payment.model.ProcessedCommand;
import com.demo.meshpay.payment.repository.ProcessedCommandRepository;
import com.demo.meshpay.payment.service.PaymentSettlementService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@Slf4j
@RequiredArgsConstructor
public class SagaCommandConsumer {
    private final PaymentSettlementService paymentSettlementService;
    private final ProcessedCommandRepository processedCommands;
    private final ObjectMapper mapper;

    @KafkaListener(topics = "saga-validate-payment-command", groupId = "payment-service-group")
    @Transactional
    public void handleValidatePaymentCommand(String message) throws Exception {
        JsonNode command = mapper.readTree(message);
        String commandId = command.path("sagaId").asText() + ":VALIDATE";
        if (alreadyProcessed(commandId)) return;
        paymentSettlementService.validatePayment(packetId(command), hash(command), text(command, "senderVpa"),
                text(command, "receiverVpa"), amount(command), text(command, "nonce"), command.path("signedAt").asLong(), commandId);
        markProcessed(commandId);
        log.info("Processed payment validation command: sagaId={}", command.path("sagaId").asText());
    }

    @KafkaListener(topics = "saga-request-settlement-command", groupId = "payment-service-group")
    @Transactional
    public void handleRequestSettlementCommand(String message) throws Exception {
        JsonNode command = mapper.readTree(message);
        String commandId = command.path("sagaId").asText() + ":REQUEST_SETTLEMENT";
        if (alreadyProcessed(commandId)) return;
        paymentSettlementService.requestSettlement(packetId(command), hash(command), text(command, "senderVpa"),
                text(command, "receiverVpa"), amount(command), text(command, "nonce"), command.path("signedAt").asLong(), commandId);
        markProcessed(commandId);
        log.info("Processed payment settlement request command: sagaId={}", command.path("sagaId").asText());
    }

    @KafkaListener(topics = "saga-complete-settlement-command", groupId = "payment-service-group")
    @Transactional
    public void handleCompleteSettlementCommand(String message) throws Exception {
        JsonNode command = mapper.readTree(message);
        String commandId = command.path("sagaId").asText() + ":COMPLETE_SETTLEMENT";
        if (alreadyProcessed(commandId)) return;
        paymentSettlementService.markPaymentSettled(packetId(command), hash(command), text(command, "senderVpa"),
                text(command, "receiverVpa"), amount(command), commandId);
        markProcessed(commandId);
        log.info("Processed payment settlement completion command: sagaId={}", command.path("sagaId").asText());
    }

    private boolean alreadyProcessed(String commandId) { return processedCommands.existsByCommandId(commandId); }
    private void markProcessed(String commandId) { processedCommands.save(new ProcessedCommand(commandId)); }
    private String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("Command field is missing: " + field);
        return value;
    }
    private String packetId(JsonNode node) { return text(node, "packetId"); }
    private String hash(JsonNode node) { return text(node, "packetHash"); }
    private BigDecimal amount(JsonNode node) {
        if (!node.hasNonNull("amount")) throw new IllegalArgumentException("Command amount is missing");
        return node.get("amount").decimalValue();
    }
}
