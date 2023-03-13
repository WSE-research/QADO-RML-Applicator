FROM gradle:7.6 AS build
COPY . .
RUN gradle installDist

FROM openjdk:latest
RUN microdnf install findutils
COPY --from=build /home/gradle/build/install/qado-rml-applicator/ /qado-rml-applicator
COPY openapi/ /qado-rml-applicator/bin/openapi/
WORKDIR /qado-rml-applicator/bin
CMD ./qado-rml-applicator
