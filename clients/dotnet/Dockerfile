# Build stage for .NET client
FROM mcr.microsoft.com/dotnet/sdk:8.0

WORKDIR /app

# Copy the solution file
COPY Goodmem.Client.sln .

# Copy the projects
COPY Pairsystems.Goodmem.Client/ ./Pairsystems.Goodmem.Client/
COPY Pairsystems.Goodmem.Client.Tests/ ./Pairsystems.Goodmem.Client.Tests/

# Restore packages, build, and test
RUN dotnet restore
RUN dotnet build --configuration Release
RUN dotnet test --no-build --configuration Release

# List all DLLs for debugging
RUN echo "Available DLLs:" && find /app -name "*.dll" | grep -v obj

# The outputs will be in /app/Pairsystems.Goodmem.Client/bin/Release/
