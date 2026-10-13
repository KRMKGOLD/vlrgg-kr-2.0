// macOS asset export; uses the system SVG renderer, with no app dependencies.
import AppKit

struct IconPath {
    let id: String
    let color: String
    let data: String
}

final class SVGPaths: NSObject, XMLParserDelegate {
    var paths: [IconPath] = []
    func parser(_ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName: String?, attributes: [String: String]) {
        guard name == "path", let id = attributes["id"], let color = attributes["fill"], let data = attributes["d"] else { return }
        paths.append(IconPath(id: id, color: color, data: data))
    }
}

let directory = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
let root = directory.deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
let resources = root.appendingPathComponent("app/androidApp/src/main/res")
let source = try String(contentsOf: directory.appendingPathComponent("app-icon.svg"), encoding: .utf8)
let parser = XMLParser(data: Data(source.utf8))
let delegate = SVGPaths()
parser.delegate = delegate
precondition(parser.parse(), "Invalid SVG")
precondition(delegate.paths.count == 3 && delegate.paths[0].id == "background", "Expected background and two foreground paths")
let foreground = Array(delegate.paths.dropFirst())

func vector(_ paths: [IconPath], size: Int = 108, monochrome: Bool = false) -> String {
    // Android's icon-background splash contract requires a 240dp canvas.
    let splashLint = size == 240 ? "\n    xmlns:tools=\"http://schemas.android.com/tools\"\n    tools:ignore=\"VectorRaster\"" : ""
    let body = paths.map {
        "    <path android:fillColor=\"\(monochrome ? "#111823" : $0.color)\" android:pathData=\"\($0.data)\" />"
    }.joined(separator: "\n")
    return """
    <?xml version="1.0" encoding="utf-8"?>
    <!-- Generated from docs/design/app-icon/app-icon.svg by export.swift. -->
    <vector xmlns:android="http://schemas.android.com/apk/res/android"\(splashLint)
        android:width="\(size)dp"
        android:height="\(size)dp"
        android:viewportWidth="108"
        android:viewportHeight="108">
    \(body)
    </vector>

    """
}

for (name, content) in [
    ("ic_launcher_background", vector([delegate.paths[0]])),
    ("ic_launcher_foreground", vector(foreground)),
    ("ic_launcher_monochrome", vector(foreground, monochrome: true)),
    ("ic_splash", vector(foreground, size: 240)),
] {
    try content.write(to: resources.appendingPathComponent("drawable/\(name).xml"), atomically: true, encoding: .utf8)
}

// The launcher shows the inner 72dp of an adaptive icon's 108dp canvas.
let cropped = source.replacingOccurrences(of: "viewBox=\"0 0 108 108\"", with: "viewBox=\"18 18 72 72\"")
let image = NSImage(data: Data(cropped.utf8))!
func png(size: Int, shape: String, destination: URL) throws {
    let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: size, pixelsHigh: size, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 32)!
    let rect = NSRect(x: 0, y: 0, width: size, height: size)
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
    NSGraphicsContext.current!.imageInterpolation = .high
    if shape == "round" { NSBezierPath(ovalIn: rect).addClip() }
    if shape == "legacy" { NSBezierPath(roundedRect: rect, xRadius: CGFloat(size) * 0.22, yRadius: CGFloat(size) * 0.22).addClip() }
    image.draw(in: rect)
    NSGraphicsContext.restoreGraphicsState()
    let data = bitmap.representation(using: .png, properties: [:])!
    precondition(data.count < 1_048_576, "PNG exceeds 1MB")
    try data.write(to: destination)
}

for (density, size) in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)] {
    for (name, shape) in [("ic_launcher", "legacy"), ("ic_launcher_round", "round")] {
        try png(size: size, shape: shape, destination: resources.appendingPathComponent("mipmap-\(density)/\(name).png"))
    }
}
try png(size: 512, shape: "square", destination: directory.appendingPathComponent("play-icon-512.png"))
print("Exported 4 VectorDrawables, 10 legacy icons and the 512px Play icon.")
