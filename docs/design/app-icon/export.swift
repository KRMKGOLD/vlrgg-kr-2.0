// macOS asset export from the approved raster source. AppKit/CoreGraphics/ImageIO/CryptoKit only.
// Run: swift docs/design/app-icon/export.swift
import AppKit
import CryptoKit
import Foundation
import ImageIO

// MARK: - Inputs and outputs

let approvedSHA256 = "239fabcbc510363bc1eb14c5f7d690ce78ec44e1784f69b82f87a4ab8bdcf186"
let side = 1254
let pixelCount = side * side
let sourceCenter = Double(side) / 2

let directory = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
let root = directory.deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
let resources = root.appendingPathComponent("app/androidApp/src/main/res")
let sourceURL = directory.appendingPathComponent("app-icon-source.png")
let densities: [(name: String, scale: Int)] = [("mdpi", 2), ("hdpi", 3), ("xhdpi", 4), ("xxhdpi", 6), ("xxxhdpi", 8)] // scale is in half-dp units

// Adaptive icon geometry (dp). The ticket must stay inside the 66dp safe zone (radius 33dp).
let adaptiveCanvas = 108.0
let safeRadius = 33.0
let targetRadius = 32.3
// Matte tuning, in 0...255 RGB distance from the sampled background unless noted.
let edgeBandWidth = 24          // border band used to sample the background colour
let hardThreshold: Float = 56   // pixels farther than this from the background are "ticket" for the flood fill
let softBand = 4                // soft alpha is computed only this many px around the hard boundary
let alphaFloor: Float = 0.06    // projected alpha below this is background noise
let radiusAlpha: Float = 0.1    // alpha that counts as visible for the safe-zone measurement
let paperLow: Float = 0.30      // max(R,G,B) at or below this is ink (V, stub bars)
let paperHigh: Float = 0.45     // max(R,G,B) at or above this is paper (red, cream)

// MARK: - Source identity and decoding

let sourceData = try Data(contentsOf: sourceURL)
let digest = SHA256.hash(data: sourceData).map { String(format: "%02x", $0) }.joined()
precondition(digest == approvedSHA256, "app-icon-source.png is not the approved source: \(digest)")

func run(_ executable: String, _ arguments: [String]) -> String {
    let process = Process()
    process.executableURL = URL(fileURLWithPath: executable)
    process.arguments = arguments
    let pipe = Pipe()
    process.standardOutput = pipe
    try! process.run()
    let output = pipe.fileHandleForReading.readDataToEndOfFile()
    process.waitUntilExit()
    precondition(process.terminationStatus == 0, "\(executable) failed")
    return String(decoding: output, as: UTF8.self)
}

// The approved PNG carries no ICC profile; it is interpreted as sRGB and every output is tagged sRGB.
let sourceInfo = run("/usr/bin/sips", ["-g", "space", "-g", "profile", sourceURL.path])
precondition(sourceInfo.contains("space: RGB"), "Expected an RGB source")
precondition(sourceInfo.contains("profile: <nil>") || sourceInfo.contains("sRGB"), "Unexpected source profile:\n\(sourceInfo)")
let sRGB = CGColorSpace(name: CGColorSpace.sRGB)!

let decoder = CGImageSourceCreateWithData(sourceData as CFData, nil)!
let sourceImage = CGImageSourceCreateImageAtIndex(decoder, 0, nil)!
precondition(sourceImage.width == side && sourceImage.height == side, "Expected a \(side)×\(side) source")
precondition(sourceImage.bitsPerComponent == 8 && [24, 32].contains(sourceImage.bitsPerPixel), "Expected 8-bit RGB")
precondition([.none, .noneSkipLast].contains(sourceImage.alphaInfo), "Expected an opaque source")
let byteOrder = sourceImage.bitmapInfo.rawValue & CGBitmapInfo.byteOrderMask.rawValue
precondition(byteOrder == 0 || byteOrder == CGImageByteOrderInfo.order32Big.rawValue, "Unexpected byte order")

// Raw decoded bytes are read directly so no colour conversion happens on the way in.
let sourceBytes = [UInt8](sourceImage.dataProvider!.data! as Data)
let sourceStride = sourceImage.bytesPerRow
let sourceStep = sourceImage.bitsPerPixel / 8
var sourceRGB = [Float](repeating: 0, count: pixelCount * 3)
for y in 0..<side {
    for x in 0..<side {
        let offset = y * sourceStride + x * sourceStep
        for c in 0..<3 { sourceRGB[(y * side + x) * 3 + c] = Float(sourceBytes[offset + c]) / 255 }
    }
}
func sourceByte(_ x: Int, _ y: Int, _ c: Int) -> UInt8 { sourceBytes[y * sourceStride + x * sourceStep + c] }

// MARK: - Background colour

func median(_ histogram: [Int], count: Int) -> Int {
    var seen = 0
    for (value, n) in histogram.enumerated() {
        seen += n
        if seen * 2 >= count { return value }
    }
    return 255
}
var histograms = [[Int]](repeating: [Int](repeating: 0, count: 256), count: 3)
var bandCount = 0
for y in 0..<side {
    for x in 0..<side where x < edgeBandWidth || y < edgeBandWidth || x >= side - edgeBandWidth || y >= side - edgeBandWidth {
        for c in 0..<3 { histograms[c][Int(sourceByte(x, y, c))] += 1 }
        bandCount += 1
    }
}
let background = (0..<3).map { median(histograms[$0], count: bandCount) }
let backgroundF = background.map { Float($0) / 255 }
let backgroundHex = background.map { String(format: "%02X", $0) }.joined()

// MARK: - Ticket matte

var distance = [Float](repeating: 0, count: pixelCount)
for p in 0..<pixelCount {
    var sum: Float = 0
    for c in 0..<3 {
        let delta = (sourceRGB[p * 3 + c] - backgroundF[c]) * 255
        sum += delta * delta
    }
    distance[p] = sum.squareRoot()
}

let neighbours8 = [(-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)]
let neighbours4 = [(0, -1), (-1, 0), (1, 0), (0, 1)]

/// Breadth-first flood over `passable` pixels from `seeds`. Returns the chessboard/manhattan step count, or -1 if unreached.
func flood(seeds: [Int], neighbours: [(Int, Int)], passable: (Int) -> Bool) -> [Int32] {
    var steps = [Int32](repeating: -1, count: pixelCount)
    var queue = [Int](); queue.reserveCapacity(pixelCount)
    for seed in seeds where steps[seed] < 0 { steps[seed] = 0; queue.append(seed) }
    var head = 0
    while head < queue.count {
        let p = queue[head]; head += 1
        let x = p % side, y = p / side
        for (dx, dy) in neighbours {
            let nx = x + dx, ny = y + dy
            guard nx >= 0, ny >= 0, nx < side, ny < side else { continue }
            let n = ny * side + nx
            if steps[n] < 0 && passable(n) { steps[n] = steps[p] + 1; queue.append(n) }
        }
    }
    return steps
}

// Everything reachable from the image border through background-like pixels is outside the ticket.
// Enclosed background-like pixels (the black V and the stub bars) therefore stay opaque.
let borderSeeds = (0..<pixelCount).filter { p in
    let x = p % side, y = p / side
    return (x == 0 || y == 0 || x == side - 1 || y == side - 1) && distance[p] <= hardThreshold
}
let outsideSteps = flood(seeds: borderSeeds, neighbours: neighbours4) { distance[$0] <= hardThreshold }
var interior = outsideSteps.map { $0 < 0 }

// Keep the single ticket component; isolated specks of texture or dust are dropped.
var componentLabel = [Int32](repeating: -1, count: pixelCount)
var componentSizes = [Int]()
for start in 0..<pixelCount where interior[start] && componentLabel[start] < 0 {
    let label = Int32(componentSizes.count)
    var stack = [start]
    componentLabel[start] = label
    var size = 0
    while let p = stack.popLast() {
        size += 1
        let x = p % side, y = p / side
        for (dx, dy) in neighbours8 {
            let nx = x + dx, ny = y + dy
            guard nx >= 0, ny >= 0, nx < side, ny < side else { continue }
            let n = ny * side + nx
            if interior[n] && componentLabel[n] < 0 { componentLabel[n] = label; stack.append(n) }
        }
    }
    componentSizes.append(size)
}
let ticketLabel = Int32(componentSizes.indices.max { componentSizes[$0] < componentSizes[$1] }!)
let droppedSpecks = componentSizes.enumerated().filter { Int32($0.offset) != ticketLabel }.map(\.element)
precondition(droppedSpecks.allSatisfy { $0 <= 16 }, "Unexpected detached components: \(droppedSpecks)")
for p in 0..<pixelCount where interior[p] && componentLabel[p] != ticketLabel { interior[p] = false }

let interiorSeeds = (0..<pixelCount).filter { interior[$0] }
let exteriorSeeds = (0..<pixelCount).filter { !interior[$0] }
let stepsFromInterior = flood(seeds: interiorSeeds, neighbours: neighbours8) { _ in true }
let stepsFromExterior = flood(seeds: exteriorSeeds, neighbours: neighbours8) { _ in true }
func maxChannel(_ p: Int) -> Float { max(sourceRGB[p * 3], sourceRGB[p * 3 + 1], sourceRGB[p * 3 + 2]) }
func isCore(_ p: Int) -> Bool { interior[p] && stepsFromExterior[p] > Int32(softBand) }

// Each boundary pixel takes the paper colour of its nearest core paper pixel as the known foreground.
let paperSeeds = (0..<pixelCount).filter { isCore($0) && maxChannel($0) >= paperHigh }
var nearestPaper = [Int32](repeating: -1, count: pixelCount)
do {
    var queue = [Int](); queue.reserveCapacity(pixelCount)
    for seed in paperSeeds { nearestPaper[seed] = Int32(seed); queue.append(seed) }
    var head = 0
    while head < queue.count {
        let p = queue[head]; head += 1
        let x = p % side, y = p / side
        for (dx, dy) in neighbours8 {
            let nx = x + dx, ny = y + dy
            guard nx >= 0, ny >= 0, nx < side, ny < side else { continue }
            let n = ny * side + nx
            if nearestPaper[n] < 0 && !isCore(n) { nearestPaper[n] = nearestPaper[p]; queue.append(n) }
        }
    }
}

// Straight (un-premultiplied) foreground colour and alpha.
var alpha = [Float](repeating: 0, count: pixelCount)
var colour = [Float](repeating: 0, count: pixelCount * 3)
for p in 0..<pixelCount {
    if isCore(p) {
        alpha[p] = 1
        for c in 0..<3 { colour[p * 3 + c] = sourceRGB[p * 3 + c] }
        continue
    }
    let nearBoundary = interior[p] || stepsFromInterior[p] <= Int32(softBand)
    guard nearBoundary, nearestPaper[p] >= 0 else { continue }
    let f = Int(nearestPaper[p])
    // Closed-form matte for a known paper colour F over the known background B: C = aF + (1 - a)B.
    var dot: Float = 0, norm: Float = 0
    for c in 0..<3 {
        let fb = sourceRGB[f * 3 + c] - backgroundF[c]
        dot += (sourceRGB[p * 3 + c] - backgroundF[c]) * fb
        norm += fb * fb
    }
    let projected = min(max(dot / norm, 0), 1)
    let a = min(max((projected - alphaFloor) / (1 - 2 * alphaFloor), 0), 1)
    guard a > 0 else { continue }
    alpha[p] = a
    // Remove the dark background from the edge colour; faint pixels lean on the paper colour to avoid noise.
    let trust = min(a / 0.5, 1)
    for c in 0..<3 {
        let unmixed = min(max((sourceRGB[p * 3 + c] - (1 - a) * backgroundF[c]) / a, 0), 1)
        colour[p * 3 + c] = sourceRGB[f * 3 + c] * (1 - trust) + unmixed * trust
    }
}

func probe(_ x: Int, _ y: Int) -> Float { alpha[y * side + x] }
// V strokes and stub bars are opaque; notches and corners are transparent.
for (x, y) in [(380, 600), (660, 640), (560, 820), (1015, 345), (1070, 765)] {
    precondition(probe(x, y) == 1, "Expected opaque ink at (\(x),\(y)), got \(probe(x, y))")
}
for (x, y) in [(140, 690), (1125, 560), (10, 10), (side - 10, 10), (10, side - 10), (side - 10, side - 10)] {
    precondition(probe(x, y) == 0, "Expected transparency at (\(x),\(y)), got \(probe(x, y))")
}

// Monochrome keeps only paper (red + cream); the V and stub bars become holes.
func paperness(_ r: Float, _ g: Float, _ b: Float) -> Float {
    min(max((max(r, g, b) - paperLow) / (paperHigh - paperLow), 0), 1)
}

var foregroundPremultiplied = [Float](repeating: 0, count: pixelCount * 4)
var monochromePremultiplied = [Float](repeating: 0, count: pixelCount * 4)
var fullBleed = [Float](repeating: 1, count: pixelCount * 4)
var maxRadius = 0.0
for p in 0..<pixelCount {
    let a = alpha[p]
    for c in 0..<3 {
        foregroundPremultiplied[p * 4 + c] = colour[p * 3 + c] * a
        fullBleed[p * 4 + c] = sourceRGB[p * 3 + c]
    }
    foregroundPremultiplied[p * 4 + 3] = a
    let m = a * paperness(colour[p * 3], colour[p * 3 + 1], colour[p * 3 + 2])
    for c in 0..<4 { monochromePremultiplied[p * 4 + c] = m }
    if a >= radiusAlpha {
        // Farthest corner of the pixel from the source centre.
        let x = Double(p % side), y = Double(p / side)
        let dx = max(abs(x - sourceCenter), abs(x + 1 - sourceCenter))
        let dy = max(abs(y - sourceCenter), abs(y + 1 - sourceCenter))
        maxRadius = max(maxRadius, (dx * dx + dy * dy).squareRoot())
    }
}
precondition((560.0...660.0).contains(maxRadius), "Matte radius \(maxRadius)px is implausible; check the matte")

// dp per source pixel for the adaptive canvas, keeping the source centre at the canvas centre.
let dpPerSourcePixel = targetRadius / maxRadius
precondition(maxRadius * dpPerSourcePixel <= targetRadius + 1e-9 && targetRadius < safeRadius)

// Full-bleed square masks: the ticket must survive the iOS squircle and Play's 30% rounded corner mask.
func insideSuperellipse(_ x: Double, _ y: Double) -> Double { 1 - (pow(abs(x), 5) + pow(abs(y), 5)) }
func insideRoundedSquare(_ x: Double, _ y: Double, radius r: Double) -> Double {
    // x, y, r in units of the half side (square spans -1...1). Returns signed margin (>0 inside).
    let qx = abs(x) - (1 - r), qy = abs(y) - (1 - r)
    if qx <= 0 || qy <= 0 { return min(1 - abs(x), 1 - abs(y)) }
    return r - (qx * qx + qy * qy).squareRoot()
}
var squircleMargin = Double.infinity, playMargin = Double.infinity, iosCornerMargin = Double.infinity
for p in 0..<pixelCount where alpha[p] >= radiusAlpha {
    for (cx, cy) in [(0.0, 0.0), (1.0, 0.0), (0.0, 1.0), (1.0, 1.0)] {
        let x = (Double(p % side) + cx - sourceCenter) / sourceCenter
        let y = (Double(p / side) + cy - sourceCenter) / sourceCenter
        squircleMargin = min(squircleMargin, insideSuperellipse(x, y))
        playMargin = min(playMargin, insideRoundedSquare(x, y, radius: 0.6))       // Play: 30% of the side
        iosCornerMargin = min(iosCornerMargin, insideRoundedSquare(x, y, radius: 0.4474)) // 22.37% of the side
    }
}
precondition(squircleMargin > 0 && playMargin > 0 && iosCornerMargin > 0, "Full-bleed masks would clip the ticket")

// MARK: - Resampling (separable Lanczos-3 on premultiplied RGBA)

func lanczos3(_ x: Double) -> Double {
    if x == 0 { return 1 }
    if abs(x) >= 3 { return 0 }
    let px = Double.pi * x
    return 3 * sin(px) * sin(px / 3) / (px * px)
}

struct Taps {
    let count: Int
    let indices: [Int]
    let weights: [Float]
}

/// Output pixel `o` samples source coordinate `(o + 0.5 - outputCount / 2) / scale + sourceCenter`.
func makeTaps(outputCount: Int, scale: Double) -> Taps {
    let filterScale = min(1, scale)
    let support = 3 / filterScale
    let count = Int((support * 2).rounded(.up)) + 2
    var indices = [Int](), weights = [Float]()
    for o in 0..<outputCount {
        let u = (Double(o) + 0.5 - Double(outputCount) / 2) / scale + sourceCenter
        let first = Int((u - support - 0.5).rounded(.down))
        var row = [Double]()
        for i in first..<(first + count) { row.append(lanczos3((Double(i) + 0.5 - u) * filterScale)) }
        let total = row.reduce(0, +)
        for (k, w) in row.enumerated() {
            indices.append(min(max(first + k, 0), side - 1))
            weights.append(Float(w / total))
        }
    }
    return Taps(count: count, indices: indices, weights: weights)
}

/// Resamples a premultiplied `side`×`side` RGBA buffer to `size`×`size`, `scale` output px per source px.
func resample(_ input: [Float], size: Int, scale: Double) -> [Float] {
    let taps = makeTaps(outputCount: size, scale: scale)
    var rowsUsed = [Bool](repeating: false, count: side)
    for i in taps.indices { rowsUsed[i] = true }
    var horizontal = [Float](repeating: 0, count: side * size * 4)
    var output = [Float](repeating: 0, count: size * size * 4)
    input.withUnsafeBufferPointer { src in
        horizontal.withUnsafeMutableBufferPointer { tmp in
            taps.indices.withUnsafeBufferPointer { idx in
                taps.weights.withUnsafeBufferPointer { wts in
                    for y in 0..<side where rowsUsed[y] {
                        let rowBase = y * side * 4
                        for o in 0..<size {
                            var r: Float = 0, g: Float = 0, b: Float = 0, a: Float = 0
                            let tapBase = o * taps.count
                            for k in 0..<taps.count {
                                let w = wts[tapBase + k]
                                let s = rowBase + idx[tapBase + k] * 4
                                r += src[s] * w; g += src[s + 1] * w; b += src[s + 2] * w; a += src[s + 3] * w
                            }
                            let d = (y * size + o) * 4
                            tmp[d] = r; tmp[d + 1] = g; tmp[d + 2] = b; tmp[d + 3] = a
                        }
                    }
                }
            }
        }
    }
    horizontal.withUnsafeBufferPointer { tmp in
        output.withUnsafeMutableBufferPointer { out in
            taps.indices.withUnsafeBufferPointer { idx in
                taps.weights.withUnsafeBufferPointer { wts in
                    for oy in 0..<size {
                        let tapBase = oy * taps.count
                        for ox in 0..<size {
                            var r: Float = 0, g: Float = 0, b: Float = 0, a: Float = 0
                            for k in 0..<taps.count {
                                let w = wts[tapBase + k]
                                let s = (idx[tapBase + k] * size + ox) * 4
                                r += tmp[s] * w; g += tmp[s + 1] * w; b += tmp[s + 2] * w; a += tmp[s + 3] * w
                            }
                            // Lanczos lobes can overshoot; keep a valid premultiplied pixel.
                            let clampedA = min(max(a, 0), 1)
                            let d = (oy * size + ox) * 4
                            out[d] = min(max(r, 0), clampedA)
                            out[d + 1] = min(max(g, 0), clampedA)
                            out[d + 2] = min(max(b, 0), clampedA)
                            out[d + 3] = clampedA
                        }
                    }
                }
            }
        }
    }
    return output
}

// MARK: - PNG encoding

func byte(_ value: Float) -> UInt8 { UInt8((min(max(value, 0), 1) * 255).rounded()) }

/// Premultiplied float RGBA → straight 8-bit RGBA (or RGB when `opaque`).
func straightBytes(_ premultiplied: [Float], opaque: Bool) -> [UInt8] {
    let pixels = premultiplied.count / 4
    var out = [UInt8](); out.reserveCapacity(pixels * (opaque ? 3 : 4))
    for p in 0..<pixels {
        let a = premultiplied[p * 4 + 3]
        if opaque {
            precondition(a > 0.999, "Opaque output has transparent pixels")
            for c in 0..<3 { out.append(byte(premultiplied[p * 4 + c] / a)) }
            continue
        }
        let alphaByte = byte(a)
        if alphaByte == 0 { out.append(contentsOf: [0, 0, 0, 0]); continue }
        for c in 0..<3 { out.append(byte(premultiplied[p * 4 + c] / a)) }
        out.append(alphaByte)
    }
    return out
}

@discardableResult
func writePNG(_ bytes: [UInt8], size: Int, opaque: Bool, to urls: [URL], maxBytes: Int? = nil) throws -> Data {
    let channels = opaque ? 3 : 4
    precondition(bytes.count == size * size * channels)
    let info = CGBitmapInfo(rawValue: (opaque ? CGImageAlphaInfo.none : CGImageAlphaInfo.last).rawValue)
    let image = CGImage(width: size, height: size, bitsPerComponent: 8, bitsPerPixel: 8 * channels, bytesPerRow: size * channels,
                        space: sRGB, bitmapInfo: info, provider: CGDataProvider(data: Data(bytes) as CFData)!,
                        decode: nil, shouldInterpolate: false, intent: .defaultIntent)!
    let output = NSMutableData()
    let destination = CGImageDestinationCreateWithData(output, "public.png" as CFString, 1, nil)!
    CGImageDestinationAddImage(destination, image, nil)
    precondition(CGImageDestinationFinalize(destination), "PNG encoding failed")
    let data = output as Data
    if let maxBytes { precondition(data.count < maxBytes, "PNG exceeds \(maxBytes) bytes") }
    for url in urls {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        // Replace the file only after the new bytes are fully written.
        try data.write(to: url, options: .atomic)
    }
    return data
}

/// Decodes a written PNG into straight sRGB bytes without colour conversion, for round-trip checks.
func decodeStraight(_ data: Data) -> (bytes: [UInt8], channels: Int, colourSpace: String) {
    let image = CGImageSourceCreateImageAtIndex(CGImageSourceCreateWithData(data as CFData, nil)!, 0, nil)!
    precondition(image.bitsPerComponent == 8 && image.bitmapInfo.rawValue & CGBitmapInfo.byteOrderMask.rawValue == 0, "Unexpected decoded layout")
    let step = image.bitsPerPixel / 8
    // ImageIO may pad opaque RGB to RGBX in memory; only the colour bytes are compared.
    let channels = image.alphaInfo == .last ? 4 : 3
    precondition([.none, .noneSkipLast, .last].contains(image.alphaInfo) && step >= channels, "Unexpected decoded alpha layout")
    let raw = [UInt8](image.dataProvider!.data! as Data)
    var bytes = [UInt8]()
    for y in 0..<image.height {
        for x in 0..<image.width {
            let offset = y * image.bytesPerRow + x * step
            bytes.append(contentsOf: raw[offset..<(offset + channels)])
        }
    }
    return (bytes, channels, (image.colorSpace?.name as String?) ?? "untagged")
}

// MARK: - Shapes for legacy launcher icons

/// Coverage of a size×size pixel grid by a shape, from 8×8 supersampling (deterministic anti-aliasing).
func coverage(size: Int, inside: (Double, Double) -> Bool) -> [Float] {
    var mask = [Float](repeating: 0, count: size * size)
    for y in 0..<size {
        for x in 0..<size {
            var hits = 0
            for sy in 0..<8 {
                for sx in 0..<8 where inside(Double(x) + (Double(sx) + 0.5) / 8, Double(y) + (Double(sy) + 0.5) / 8) { hits += 1 }
            }
            mask[y * size + x] = Float(hits) / 64
        }
    }
    return mask
}

func roundedSquare(size: Int, radiusFraction: Double) -> (Double, Double) -> Bool {
    let s = Double(size), r = s * radiusFraction
    return { x, y in
        let qx = max(r - x, x - (s - r), 0), qy = max(r - y, y - (s - r), 0)
        return qx * qx + qy * qy <= r * r
    }
}

func circle(size: Int) -> (Double, Double) -> Bool {
    let c = Double(size) / 2
    return { x, y in (x - c) * (x - c) + (y - c) * (y - c) <= c * c }
}

// MARK: - Export

/// Renders the ticket cut-out on a canvas that spans `canvasDp` adaptive units, at `size` px.
func renderCutOut(_ buffer: [Float], size: Int, canvasDp: Double = adaptiveCanvas) -> [Float] {
    resample(buffer, size: size, scale: Double(size) / canvasDp * dpPerSourcePixel)
}

var written = [String]()
func path(_ url: URL) -> String { String(url.path.dropFirst(root.path.count + 1)) }

// Adaptive background colour, shared by the launcher and the splash icon background.
let colourResource = resources.appendingPathComponent("values/ic_launcher_background.xml")
try """
<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by docs/design/app-icon/export.swift: median of the source image border. -->
<resources>
    <color name="ic_launcher_background">#\(backgroundHex)</color>
</resources>

""".write(to: colourResource, atomically: true, encoding: .utf8)
written.append(path(colourResource))

/// Farthest pixel corner from the canvas centre among written pixels with alpha ≥ 0.1, in dp.
/// Measured on the final 8-bit straight RGBA bytes, so resampling spread and quantisation are included.
func visibleRadiusDp(_ bytes: [UInt8], size: Int, canvasDp: Double) -> Double {
    let minimumAlpha = UInt8((radiusAlpha * 255).rounded(.up))
    let c = Double(size) / 2
    var radius = 0.0
    for p in 0..<(size * size) where bytes[p * 4 + 3] >= minimumAlpha {
        let x = Double(p % size), y = Double(p / size)
        let dx = max(abs(x - c), abs(x + 1 - c)), dy = max(abs(y - c), abs(y + 1 - c))
        radius = max(radius, (dx * dx + dy * dy).squareRoot())
    }
    return radius * canvasDp / Double(size)
}

var renderedRadii = [String]()
var renderedForegroundRadiusDp = 0.0, renderedSplashRadiusDp = 0.0
for density in densities {
    let mipmap = resources.appendingPathComponent("mipmap-\(density.name)")
    // 108dp adaptive layers; every density must keep visible pixels inside the 66dp safe zone.
    let layerSize = 54 * density.scale
    let foregroundBytes = straightBytes(renderCutOut(foregroundPremultiplied, size: layerSize), opaque: false)
    let monochromeBytes = straightBytes(renderCutOut(monochromePremultiplied, size: layerSize), opaque: false)
    let foregroundRadius = visibleRadiusDp(foregroundBytes, size: layerSize, canvasDp: adaptiveCanvas)
    let monochromeRadius = visibleRadiusDp(monochromeBytes, size: layerSize, canvasDp: adaptiveCanvas)
    precondition(foregroundRadius <= safeRadius && monochromeRadius <= safeRadius,
                 "\(density.name) layer leaves the safe zone: foreground \(foregroundRadius)dp, monochrome \(monochromeRadius)dp")
    renderedForegroundRadiusDp = max(renderedForegroundRadiusDp, foregroundRadius, monochromeRadius)
    let foregroundURL = mipmap.appendingPathComponent("ic_launcher_foreground.png")
    try writePNG(foregroundBytes, size: layerSize, opaque: false, to: [foregroundURL])
    let monochromeURL = mipmap.appendingPathComponent("ic_launcher_monochrome.png")
    try writePNG(monochromeBytes, size: layerSize, opaque: false, to: [monochromeURL])
    written += [path(foregroundURL), path(monochromeURL)]

    // Legacy 48dp icons: the centre 72dp of background + foreground, as a rounded square and a circle.
    let legacySize = 24 * density.scale
    let legacy = resample(foregroundPremultiplied, size: legacySize, scale: Double(legacySize) / 72 * dpPerSourcePixel)
    for (name, shape) in [("ic_launcher", roundedSquare(size: legacySize, radiusFraction: 0.22)), ("ic_launcher_round", circle(size: legacySize))] {
        let mask = coverage(size: legacySize, inside: shape)
        var composed = [Float](repeating: 0, count: legacySize * legacySize * 4)
        for p in 0..<(legacySize * legacySize) {
            let a = legacy[p * 4 + 3]
            for c in 0..<3 { composed[p * 4 + c] = (legacy[p * 4 + c] + (1 - a) * backgroundF[c]) * mask[p] }
            composed[p * 4 + 3] = mask[p]
        }
        let url = mipmap.appendingPathComponent("\(name).png")
        try writePNG(straightBytes(composed, opaque: false), size: legacySize, opaque: false, to: [url])
        written.append(path(url))
    }

    // Splash icon: 240dp canvas with the same 108-unit composition, shown inside a 160dp circle.
    let splashSize = 120 * density.scale
    let splashBytes = straightBytes(renderCutOut(foregroundPremultiplied, size: splashSize), opaque: false)
    let splashRadius = visibleRadiusDp(splashBytes, size: splashSize, canvasDp: 240)
    precondition(splashRadius <= 80, "\(density.name) splash leaves the 160dp circle: \(splashRadius)dp")
    renderedSplashRadiusDp = max(renderedSplashRadiusDp, splashRadius)
    let splashURL = resources.appendingPathComponent("drawable-\(density.name)/ic_splash.png")
    try writePNG(splashBytes, size: splashSize, opaque: false, to: [splashURL])
    written.append(path(splashURL))
    renderedRadii.append(String(format: "%@ foreground %.2f / monochrome %.2f / splash %.2f", density.name, foregroundRadius, monochromeRadius, splashRadius))
}

// Full-bleed squares: iOS (opaque RGB), Google Play (RGBA, < 1MB) and the About screen.
let iosURL = root.appendingPathComponent("app/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png")
let ios = resample(fullBleed, size: 1024, scale: 1024 / Double(side))
let iosBytes = straightBytes(ios, opaque: true)
let iosData = try writePNG(iosBytes, size: 1024, opaque: true, to: [iosURL])
let playURLs = [directory.appendingPathComponent("play-icon-512.png"), root.appendingPathComponent("fastlane/metadata/android/ko-KR/images/icon.png")]
let playData = try writePNG(straightBytes(resample(fullBleed, size: 512, scale: 512 / Double(side)), opaque: false), size: 512, opaque: false, to: playURLs, maxBytes: 1_048_576)
let aboutURL = root.appendingPathComponent("app/shared/src/commonMain/composeResources/drawable/app_icon.png")
try writePNG(straightBytes(resample(fullBleed, size: 224, scale: 224 / Double(side)), opaque: false), size: 224, opaque: false, to: [aboutURL])
written += [path(iosURL)] + playURLs.map(path) + [path(aboutURL)]

// Colour pipeline check: the written iOS PNG decodes to the same sRGB bytes, and a flat background
// pixel matches the source pixels it was resampled from.
let decodedIOS = decodeStraight(iosData)
precondition(decodedIOS.channels == 3 && decodedIOS.bytes == iosBytes, "iOS PNG round trip changed pixels")
precondition(decodedIOS.colourSpace.contains("SRGB"), "iOS PNG is not tagged sRGB: \(decodedIOS.colourSpace)")
let probeOut = (x: 40, y: 40)
let probeSource = Int((Double(probeOut.x) + 0.5) * Double(side) / 1024)
for c in 0..<3 {
    var sum = 0
    for dy in -1...1 { for dx in -1...1 { sum += Int(sourceByte(probeSource + dx, probeSource + dy, c)) } }
    let expected = Double(sum) / 9
    let actual = Double(decodedIOS.bytes[(probeOut.y * 1024 + probeOut.x) * 3 + c])
    precondition(abs(actual - expected) <= 3, "Output colour drifted from the source: \(actual) vs \(expected)")
}
precondition(decodeStraight(playData).channels == 4, "Play icon must be 32-bit RGBA")

print("""
Source: \(digest) (\(side)×\(side), \(sourceInfo.contains("profile: <nil>") ? "untagged, treated as sRGB" : "sRGB"))
Background #\(backgroundHex); matte max radius \(String(format: "%.1f", maxRadius))px from centre; dropped specks \(droppedSpecks)
Adaptive scale: \(String(format: "%.5f", dpPerSourcePixel))dp per source px (source = \(String(format: "%.2f", Double(side) * dpPerSourcePixel))dp); \
ticket radius \(targetRadius)dp; rendered max \(String(format: "%.2f", renderedForegroundRadiusDp))dp ≤ \(safeRadius)dp, splash max \(String(format: "%.2f", renderedSplashRadiusDp))dp ≤ 80dp
Rendered radius (dp, alpha ≥ 0.1, farthest pixel corner):
\(renderedRadii.map { "  " + $0 }.joined(separator: "\n"))
Full-bleed mask margins (half-side units): squircle n=5 \(String(format: "%.3f", squircleMargin)), Play 30% \(String(format: "%.3f", playMargin)), 22.37% \(String(format: "%.3f", iosCornerMargin))
Play icon \(playData.count) bytes; iOS icon \(iosData.count) bytes
Wrote \(written.count) files:
\(written.map { "  " + $0 }.joined(separator: "\n"))
""")
