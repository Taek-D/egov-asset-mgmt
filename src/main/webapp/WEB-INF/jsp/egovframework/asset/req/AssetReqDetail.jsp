<%--
  신청서 상세: 헤더, 품목, 처리 이력, 보는 사람이 할 수 있는 처리(신청취소·승인·지급·지급취소)
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>신청서 상세</title>
<link type="text/css" rel="stylesheet" href="<c:url value='/css/egovframework/com/com.css' />">
</head>
<body>

<div class="wTableFrm">
	<h2>신청서 상세</h2>

	<c:if test="${not empty message}">
	<p role="alert" style="margin:0 0 7px; padding:8px 10px; border:1px solid #b1bbcb; background:#fff8e5;"><c:out value="${message}"/></p>
	</c:if>

	<%@ include file="AssetReqHeader.jspf" %>

	<h2 style="clear:both; margin-top:20px;">신청 품목</h2>
	<table class="board_list">
	<caption>신청 품목</caption>
	<colgroup><col><col style="width:11%;"><col style="width:11%;"><col style="width:11%;"><col style="width:11%;"><col style="width:24%;"></colgroup>
	<thead>
	<tr>
		<th scope="col">품목</th>
		<th scope="col">신청 수량</th>
		<th scope="col">승인 수량</th>
		<th scope="col">결과</th>
		<th scope="col">현재 가용</th>
		<th scope="col">반려 사유</th>
	</tr>
	</thead>
	<tbody>
	<c:forEach items="${req.lines}" var="ln">
	<tr>
		<td class="left"><c:out value="${ln.itemNm}"/><c:if test="${ln.useAt != 'Y'}"> (사용 중지)</c:if></td>
		<td><c:out value="${ln.reqQty}"/></td>
		<td><c:out value="${ln.aprvQty}"/></td>
		<td><c:out value="${lineSttusNm[ln.lineSttus]}"/></td>
		<td><c:out value="${ln.availQty}"/><c:if test="${req.sttus == 'PENDING' && ln.reqQty > ln.availQty}"><br/><span style="color:#c00;">가용보다 많음(승인 때 확인)</span></c:if></td>
		<td class="left"><c:out value="${ln.rejectResn}"/></td>
	</tr>
	</c:forEach>
	</tbody>
	</table>

	<div class="btn">
		<c:if test="${req.approvable}">
		<span class="btn_s"><a href="<c:url value='/asset/approve/form.do'><c:param name='reqNo' value='${req.reqNo}'/></c:url>" title="승인 화면으로 이동">승인 처리</a></span>
		</c:if>
		<c:if test="${req.deliverable}">
		<span class="btn_s"><a href="<c:url value='/asset/deliver/form.do'><c:param name='reqNo' value='${req.reqNo}'/></c:url>" title="지급 화면으로 이동">지급</a></span>
		</c:if>
		<span class="btn_s"><a href="<c:url value='/asset/req/myList.do'/>" title="내 신청 목록으로 이동">내 신청</a></span>
	</div>

	<c:if test="${req.cancelable}">
	<form action="<c:url value='/asset/req/cancel.do'/>" method="post" onsubmit="return confirm('신청을 취소하시겠습니까? 취소한 신청은 되돌릴 수 없습니다.');" style="clear:both;">
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
	<input type="hidden" name="reqNo" value="<c:out value='${req.reqNo}'/>"/>
	<p>대기 중인 신청은 고칠 수 없습니다. 바꾸려면 취소한 뒤 다시 신청하세요.</p>
	<div class="btn"><input type="submit" class="s_submit" value="신청취소" title="신청취소 버튼"/></div>
	</form>
	</c:if>

	<c:if test="${req.deliverable}">
	<h2 style="clear:both; margin-top:20px;">지급취소</h2>
	<form action="<c:url value='/asset/deliver/cancel.do'/>" method="post" onsubmit="return confirm('지급을 취소하시겠습니까? 확보한 수량이 복구되고 되돌릴 수 없습니다.');">
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
	<input type="hidden" name="reqNo" value="<c:out value='${req.reqNo}'/>"/>
	<table class="wTable">
	<caption>지급취소</caption>
	<colgroup><col style="width:18%;"><col></colgroup>
	<tbody>
	<tr>
		<th scope="row"><label for="cancelReason">지급취소 사유 <span class="pilsu">*</span></label></th>
		<td class="left"><input type="text" id="cancelReason" name="reason" title="지급취소 사유 입력(필수)" size="60" maxlength="500" required="required"/></td>
	</tr>
	</tbody>
	</table>
	<div class="btn"><input type="submit" class="s_submit" value="지급취소" title="지급취소 버튼"/></div>
	</form>
	</c:if>

	<h2 style="clear:both; margin-top:20px;">처리 이력</h2>
	<table class="board_list">
	<caption>처리 이력</caption>
	<colgroup><col style="width:18%;"><col style="width:14%;"><col style="width:14%;"><col style="width:18%;"><col></colgroup>
	<thead>
	<tr>
		<th scope="col">처리 시각</th>
		<th scope="col">이전 상태</th>
		<th scope="col">이후 상태</th>
		<th scope="col">처리자</th>
		<th scope="col">사유</th>
	</tr>
	</thead>
	<tbody>
	<c:forEach items="${req.histList}" var="h">
	<tr>
		<td><fmt:formatDate value="${h.processPnttm}" pattern="yyyy-MM-dd HH:mm:ss"/></td>
		<td><c:choose><c:when test="${empty h.beforeSttus}">-</c:when><c:otherwise><c:out value="${sttusNm[h.beforeSttus]}"/></c:otherwise></c:choose></td>
		<td><c:out value="${sttusNm[h.afterSttus]}"/></td>
		<td><c:out value="${h.processNm}"/> (<c:out value="${h.processId}"/>)</td>
		<td class="left"><c:out value="${h.resn}"/></td>
	</tr>
	</c:forEach>
	</tbody>
	</table>
</div>

</body>
</html>
