#!/usr/bin/xcrun swift

import Foundation

private enum ValidationError: Error {
    case message(String)
}

private func reject(_ message: String) throws -> Never {
    throw ValidationError.message(message)
}

private func validateVersion(_ value: String) throws {
    let components = value.split(separator: ".", omittingEmptySubsequences: false)
    guard
        (1...3).contains(components.count),
        components.allSatisfy({
            !$0.isEmpty && $0.utf8.allSatisfy({ (48...57).contains($0) })
        })
    else {
        try reject("APP_VERSION must contain one to three numeric components.")
    }
}

private func validateBuildNumber(_ value: String) throws {
    guard
        !value.isEmpty,
        value.utf8.allSatisfy({ (48...57).contains($0) }),
        let number = UInt64(value),
        number > 0
    else {
        try reject("APP_BUILD_NUMBER must be a positive integer.")
    }
}

@discardableResult
private func validateBaseURL(_ value: String, requireHTTPS: Bool) throws -> String {
    guard !value.isEmpty, value == value.trimmingCharacters(in: .whitespacesAndNewlines) else {
        try reject("API_BASE_URL is required and cannot contain surrounding whitespace.")
    }
    guard
        value.rangeOfCharacter(from: .whitespacesAndNewlines) == nil,
        value.rangeOfCharacter(from: .controlCharacters) == nil
    else {
        try reject("API_BASE_URL cannot contain whitespace or control characters.")
    }
    guard !value.contains("$"), !value.contains("("), !value.contains(")"), !value.contains("\\") else {
        try reject("API_BASE_URL contains characters that are unsafe in an xcconfig value.")
    }
    guard
        let components = URLComponents(string: value, encodingInvalidCharacters: false),
        components.string == value,
        components.url != nil,
        let scheme = components.scheme?.lowercased(),
        let host = components.host,
        !host.isEmpty
    else {
        try reject("API_BASE_URL must be an absolute URL with a host.")
    }
    guard scheme == "http" || scheme == "https" else {
        try reject("API_BASE_URL must use HTTP or HTTPS.")
    }
    if requireHTTPS, scheme != "https" {
        try reject("Release API_BASE_URL must use HTTPS.")
    }
    guard components.user == nil, components.password == nil else {
        try reject("API_BASE_URL cannot include user information.")
    }
    guard components.query == nil, components.fragment == nil else {
        try reject("API_BASE_URL cannot include a query or fragment.")
    }
    if let port = components.port, !(1...65_535).contains(port) {
        try reject("API_BASE_URL port must be between 1 and 65535.")
    }
    guard components.percentEncodedPath.isEmpty || components.percentEncodedPath == "/" else {
        try reject("API_BASE_URL must be an origin without a path.")
    }
    return value
}

private func optionalTeamID(from environment: [String: String]) throws -> String? {
    guard let value = environment["IOS_TEAM_ID"], !value.isEmpty else {
        return nil
    }
    guard
        value.utf8.count == 10,
        value.utf8.allSatisfy({ (48...57).contains($0) || (65...90).contains($0) })
    else {
        try reject("IOS_TEAM_ID must be a 10-character uppercase alphanumeric identifier.")
    }
    return value
}

private func writeReleaseConfig(to path: String, environment: [String: String]) throws {
    guard let baseURL = environment["API_BASE_URL"] else {
        try reject("API_BASE_URL is required.")
    }
    guard let version = environment["APP_VERSION"] else {
        try reject("APP_VERSION is required.")
    }
    guard let buildNumber = environment["APP_BUILD_NUMBER"] else {
        try reject("APP_BUILD_NUMBER is required.")
    }

    try validateBaseURL(baseURL, requireHTTPS: true)
    try validateVersion(version)
    try validateBuildNumber(buildNumber)
    let teamID = try optionalTeamID(from: environment)

    var lines = [
        "SLASH=/",
        "API_BASE_URL=\(baseURL.replacingOccurrences(of: "/", with: "$(SLASH)"))",
        "MARKETING_VERSION=\(version)",
        "CURRENT_PROJECT_VERSION=\(buildNumber)",
    ]
    if let teamID {
        lines.append("TEAM_ID=\(teamID)")
    }

    let outputURL = URL(fileURLWithPath: path)
    if FileManager.default.fileExists(atPath: path) {
        let attributes = try FileManager.default.attributesOfItem(atPath: path)
        guard attributes[.type] as? FileAttributeType == .typeRegular else {
            try reject("Output path must be a regular file.")
        }
    }
    try Data((lines.joined(separator: "\n") + "\n").utf8).write(to: outputURL, options: .atomic)
    try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: path)
}

private func validatePlist(at path: String, configuration: String) throws {
    let data = try Data(contentsOf: URL(fileURLWithPath: path))
    guard
        let plist = try PropertyListSerialization.propertyList(from: data, options: [], format: nil) as? [String: Any],
        let baseURL = plist["API_BASE_URL"] as? String,
        let version = plist["CFBundleShortVersionString"] as? String,
        let buildNumber = plist["CFBundleVersion"] as? String
    else {
        try reject("Processed Info.plist is missing required app configuration.")
    }

    try validateBaseURL(baseURL, requireHTTPS: configuration == "Release")
    try validateVersion(version)
    try validateBuildNumber(buildNumber)
}

private func run() throws {
    let arguments = CommandLine.arguments
    guard arguments.count >= 2 else {
        try reject("Usage: release_config.swift generate <xcconfig> | validate-plist <plist> <configuration>")
    }

    switch arguments[1] {
    case "generate" where arguments.count == 3:
        try writeReleaseConfig(to: arguments[2], environment: ProcessInfo.processInfo.environment)
    case "validate-plist" where arguments.count == 4:
        try validatePlist(at: arguments[2], configuration: arguments[3])
    default:
        try reject("Invalid release configuration command.")
    }
}

do {
    try run()
} catch ValidationError.message(let message) {
    FileHandle.standardError.write(Data("error: \(message)\n".utf8))
    exit(1)
} catch {
    FileHandle.standardError.write(Data("error: Unable to prepare or validate app configuration.\n".utf8))
    exit(1)
}
