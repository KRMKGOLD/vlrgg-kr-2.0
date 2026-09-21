ARG BASE_IMAGE
FROM ${BASE_IMAGE}

COPY --chown=app:app validation.jar /app/validation.jar

ENV VLRGG_OBSERVABILITY_VALIDATION=true
ENTRYPOINT ["java", "-Xms128m", "-Xmx384m", "-XX:+ExitOnOutOfMemoryError", "-cp", "/app/lib/*:/app/validation.jar", "kr.co.cotton.vlrgg_mobile.observability.validation.ObservabilityValidationMainKt"]
