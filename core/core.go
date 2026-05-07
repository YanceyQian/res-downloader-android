package core

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/elazarl/goproxy"
	"github.com/matoous/go-nanoid/v2"
)

type ResourceInfo struct {
	ID        string            `json:"id"`
	URL       string            `json:"url"`
	Type      string            `json:"type"`
	Platform  string            `json:"platform"`
	Filename  string            `json:"filename"`
	Size      int64             `json:"size"`
	Timestamp int64             `json:"timestamp"`
	Headers   map[string]string `json:"headers,omitempty"`
}

type ProxyServer struct {
	port          int
	proxy         *goproxy.ProxyHttpServer
	server        *http.Server
	resources     map[string]*ResourceInfo
	mu            sync.RWMutex
	onResource    func(*ResourceInfo)
	ctx           context.Context
	cancel        context.CancelFunc
}

var (
	instance *ProxyServer
	once     sync.Once
)

func NewProxyServer(port int) *ProxyServer {
	once.Do(func() {
		ctx, cancel := context.WithCancel(context.Background())
		instance = &ProxyServer{
			port:      port,
			proxy:     goproxy.NewProxyHttpServer(),
			resources: make(map[string]*ResourceInfo),
			ctx:       ctx,
			cancel:    cancel,
		}
		instance.setupProxy()
	})
	instance.port = port
	return instance
}

func (p *ProxyServer) setupProxy() {
	p.proxy.Verbose = false

	p.proxy.OnRequest().DoFunc(func(r *http.Request, ctx *goproxy.ProxyCtx) (*http.Request, *http.Response) {
		resource := p.parseResource(r)
		if resource != nil {
			p.addResource(resource)
			if p.onResource != nil {
				p.onResource(resource)
			}
		}
		return r, nil
	})

	p.proxy.OnResponse().DoFunc(func(r *http.Response, ctx *goproxy.ProxyCtx) *http.Response {
		if r != nil && r.Request != nil {
			resource := p.parseResourceFromResponse(r)
			if resource != nil {
				p.addResource(resource)
				if p.onResource != nil {
					p.onResource(resource)
				}
			}
		}
		return r
	})
}

func (p *ProxyServer) parseResource(r *http.Request) *ResourceInfo {
	urlStr := r.URL.String()
	if !p.isMediaURL(urlStr) {
		return nil
	}

	return &ResourceInfo{
		ID:        generateID(),
		URL:       urlStr,
		Type:      detectType(urlStr),
		Platform:  detectPlatform(r.Host),
		Filename:  extractFilename(r.URL.Path),
		Size:      0,
		Timestamp: time.Now().UnixMilli(),
		Headers:   extractRelevantHeaders(r.Header),
	}
}

func (p *ProxyServer) parseResourceFromResponse(r *http.Response) *ResourceInfo {
	if r == nil || r.Request == nil {
		return nil
	}

	contentType := r.Header.Get("Content-Type")
	if !isMediaContentType(contentType) {
		return nil
	}

	urlStr := r.Request.URL.String()
	return &ResourceInfo{
		ID:        generateID(),
		URL:       urlStr,
		Type:      detectTypeFromContentType(contentType),
		Platform:  detectPlatform(r.Request.Host),
		Filename:  extractFilename(r.Request.URL.Path),
		Size:      r.ContentLength,
		Timestamp: time.Now().UnixMilli(),
	}
}

func (p *ProxyServer) isMediaURL(urlStr string) bool {
	mediaExtensions := []string{".mp4", ".m3u8", ".ts", ".flv", ".mp3", ".flac", ".wav", ".aac", ".ogg", ".m4a", ".jpg", ".jpeg", ".png", ".gif", ".webp"}
	lowerURL := strings.ToLower(urlStr)
	for _, ext := range mediaExtensions {
		if strings.Contains(lowerURL, ext) {
			return true
		}
	}
	return false
}

func isMediaContentType(contentType string) bool {
	mediaTypes := []string{"video", "audio", "image", "application/x-mpegURL", "application/vnd.apple.mpegurl"}
	lowerCT := strings.ToLower(contentType)
	for _, mt := range mediaTypes {
		if strings.Contains(lowerCT, mt) {
			return true
		}
	}
	return false
}

func detectType(urlStr string) string {
	lowerURL := strings.ToLower(urlStr)
	switch {
	case strings.Contains(lowerURL, ".m3u8"):
		return "m3u8"
	case strings.Contains(lowerURL, ".mp4") || strings.Contains(lowerURL, ".flv") || strings.Contains(lowerURL, ".ts"):
		return "video"
	case strings.Contains(lowerURL, ".mp3") || strings.Contains(lowerURL, ".flac") || strings.Contains(lowerURL, ".wav") || strings.Contains(lowerURL, ".m4a"):
		return "audio"
	case strings.Contains(lowerURL, ".jpg") || strings.Contains(lowerURL, ".png") || strings.Contains(lowerURL, ".gif") || strings.Contains(lowerURL, ".webp"):
		return "image"
	default:
		return "other"
	}
}

func detectTypeFromContentType(contentType string) string {
	lowerCT := strings.ToLower(contentType)
	switch {
	case strings.Contains(lowerCT, "video"):
		if strings.Contains(lowerCT, "mpegurl") || strings.Contains(lowerCT, "x-mpegurl") {
			return "m3u8"
		}
		return "video"
	case strings.Contains(lowerCT, "audio"):
		return "audio"
	case strings.Contains(lowerCT, "image"):
		return "image"
	default:
		return "other"
	}
}

func detectPlatform(host string) string {
	lowerHost := strings.ToLower(host)
	switch {
	case strings.Contains(lowerHost, "weixin.qq.com") || strings.Contains(lowerHost, "wechat.com"):
		return "wechat"
	case strings.Contains(lowerHost, "douyin.com") || strings.Contains(lowerHost, "iesdouyin.com"):
		return "douyin"
	case strings.Contains(lowerHost, "kuaishou.com"):
		return "kuaishou"
	case strings.Contains(lowerHost, "xiaohongshu.com"):
		return "xiaohongshu"
	case strings.Contains(lowerHost, "kugou.com"):
		return "kugou"
	case strings.Contains(lowerHost, "y.qq.com") || strings.Contains(lowerHost, "music.qq.com"):
		return "qqmusic"
	default:
		return "other"
	}
}

func extractFilename(path string) string {
	parts := strings.Split(path, "/")
	filename := parts[len(parts)-1]
	if strings.Contains(filename, "?") {
		filename = strings.Split(filename, "?")[0]
	}
	if filename == "" {
		return "download"
	}
	return filename
}

func extractRelevantHeaders(header http.Header) map[string]string {
	relevantHeaders := []string{"User-Agent", "Referer", "Cookie", "Accept", "Range"}
	result := make(map[string]string)
	for _, h := range relevantHeaders {
		if val := header.Get(h); val != "" {
			result[h] = val
		}
	}
	return result
}

func generateID() string {
	id, _ := gonanoid.New()
	return id
}

func (p *ProxyServer) addResource(resource *ResourceInfo) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.resources[resource.ID] = resource
}

func (p *ProxyServer) Start() error {
	p.server = &http.Server{
		Addr:    fmt.Sprintf(":%d", p.port),
		Handler: p.proxy,
	}
	return p.server.ListenAndServe()
}

func (p *ProxyServer) Stop() error {
	if p.cancel != nil {
		p.cancel()
	}
	if p.server != nil {
		return p.server.Shutdown(context.Background())
	}
	return nil
}

func (p *ProxyServer) GetResources() []*ResourceInfo {
	p.mu.RLock()
	defer p.mu.RUnlock()

	resources := make([]*ResourceInfo, 0, len(p.resources))
	for _, r := range p.resources {
		resources = append(resources, r)
	}
	return resources
}

func (p *ProxyServer) GetResource(id string) *ResourceInfo {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.resources[id]
}

func (p *ProxyServer) RemoveResource(id string) bool {
	p.mu.Lock()
	defer p.mu.Unlock()
	if _, exists := p.resources[id]; exists {
		delete(p.resources, id)
		return true
	}
	return false
}

func (p *ProxyServer) ClearResources() {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.resources = make(map[string]*ResourceInfo)
}

func (p *ProxyServer) SetResourceCallback(callback func(*ResourceInfo)) {
	p.onResource = callback
}

func (p *ProxyServer) IsRunning() bool {
	return p.server != nil
}

type Downloader struct {
	client *http.Client
}

func NewDownloader() *Downloader {
	return &Downloader{
		client: &http.Client{
			Timeout: 30 * time.Minute,
		},
	}
}

func (d *Downloader) Download(urlStr string, output io.Writer, headers map[string]string) (int64, error) {
	req, err := http.NewRequest("GET", urlStr, nil)
	if err != nil {
		return 0, err
	}

	for k, v := range headers {
		req.Header.Set(k, v)
	}

	resp, err := d.client.Do(req)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusPartialContent {
		return 0, fmt.Errorf("unexpected status code: %d", resp.StatusCode)
	}

	return io.Copy(output, resp.Body)
}

func (d *Downloader) DownloadWithProgress(urlStr string, output io.Writer, headers map[string]string, progressCallback func(downloaded, total int64)) (int64, error) {
	req, err := http.NewRequest("GET", urlStr, nil)
	if err != nil {
		return 0, err
	}

	for k, v := range headers {
		req.Header.Set(k, v)
	}

	resp, err := d.client.Do(req)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusPartialContent {
		return 0, fmt.Errorf("unexpected status code: %d", resp.StatusCode)
	}

	total := resp.ContentLength
	downloaded := int64(0)
	buffer := make([]byte, 32*1024)

	for {
		n, err := resp.Body.Read(buffer)
		if n > 0 {
			_, writeErr := output.Write(buffer[:n])
			if writeErr != nil {
				return downloaded, writeErr
			}
			downloaded += int64(n)
			if progressCallback != nil {
				progressCallback(downloaded, total)
			}
		}
		if err == io.EOF {
			break
		}
		if err != nil {
			return downloaded, err
		}
	}

	return downloaded, nil
}

type M3U8Parser struct{}

func NewM3U8Parser() *M3U8Parser {
	return &M3U8Parser{}
}

type M3U8Playlist struct {
	Segments []M3U8Segment
	BaseURL string
}

type M3U8Segment struct {
	URL      string
	Duration float64
	Seq      int
}

func (p *M3U8Parser) Parse(content string, baseURL string) (*M3U8Playlist, error) {
	playlist := &M3U8Playlist{
		Segments: make([]M3U8Segment, 0),
		BaseURL:  baseURL,
	}

	scanner := bufio.NewScanner(strings.NewReader(content))
	var seq int
	var currentDuration float64

	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())

		if strings.HasPrefix(line, "#EXTINF:") {
			fmt.Sscanf(line, "#EXTINF:%f,", &currentDuration)
		} else if line != "" && !strings.HasPrefix(line, "#") {
			segmentURL := line
			if !strings.HasPrefix(segmentURL, "http") {
				if strings.HasSuffix(playlist.BaseURL, "/") && strings.HasPrefix(segmentURL, "/") {
					segmentURL = playlist.BaseURL + segmentURL[1:]
				} else if !strings.HasSuffix(playlist.BaseURL, "/") && !strings.HasPrefix(segmentURL, "/") {
					segmentURL = playlist.BaseURL + "/" + segmentURL
				} else {
					segmentURL = playlist.BaseURL + segmentURL
				}
			}

			playlist.Segments = append(playlist.Segments, M3U8Segment{
				URL:      segmentURL,
				Duration: currentDuration,
				Seq:      seq,
			})
			seq++
			currentDuration = 0
		}
	}

	return playlist, scanner.Err()
}

type VersionChecker struct {
	repoOwner string
	repoName  string
	client    *http.Client
}

func NewVersionChecker(repoOwner, repoName string) *VersionChecker {
	return &VersionChecker{
		repoOwner: repoOwner,
		repoName:  repoName,
		client: &http.Client{
			Timeout: 10 * time.Second,
		},
	}
}

type ReleaseInfo struct {
	TagName    string   `json:"tag_name"`
	Name       string   `json:"name"`
	Body       string   `json:"body"`
	HTMLURL    string   `json:"html_url"`
	Assets     []Asset  `json:"assets"`
}

type Asset struct {
	Name               string `json:"name"`
	BrowserDownloadURL string `json:"browser_download_url"`
	Size               int64  `json:"size"`
}

func (v *VersionChecker) GetLatestRelease() (*ReleaseInfo, error) {
	url := fmt.Sprintf("https://api.github.com/repos/%s/%s/releases/latest", v.repoOwner, v.repoName)

	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}

	req.Header.Set("Accept", "application/vnd.github.v3+json")

	resp, err := v.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("failed to fetch release: %d", resp.StatusCode)
	}

	var release ReleaseInfo
	if err := json.NewDecoder(resp.Body).Decode(&release); err != nil {
		return nil, err
	}

	return &release, nil
}

func ParseURL(rawURL string) (*url.URL, error) {
	return url.Parse(rawURL)
}
