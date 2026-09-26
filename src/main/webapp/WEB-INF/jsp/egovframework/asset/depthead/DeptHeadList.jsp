<%--
  부서장 지정(관리자). 부서마다 부서장 1명까지, 그 부서 소속의 로그인 가능한 직원만 지정할 수 있다.
  지정된 사람이 부서를 옮기거나 잠기거나 비활성이 되면 '무효'로 표시하고 그 부서 신청은 관리자가 승인한다.
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="ui" uri="http://egovframework.gov/ctl/ui"%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>부서장 지정</title>
<link type="text/css" rel="stylesheet" href="<c:url value='/css/egovframework/com/com.css' />">
<style>
.badge_invalid { display:inline-block; padding:1px 6px; background:#d9534f; color:#fff; font-size:11px; border-radius:2px; }
.dept_head_msg { margin:10px 0; padding:8px 10px; border:1px solid #4688d2; background:#f2f7fd; }
</style>
<script type="text/javascript">
function fn_egov_select_linkPage(pageNo) {
	location.href = "<c:url value='/asset/depthead/list.do'/>?pageIndex=" + pageNo;
}
</script>
</head>
<body>
<div class="board">
	<h1>부서장 지정</h1>

	<c:if test="${not empty message}">
		<p class="dept_head_msg" role="alert"><c:out value="${message}"/></p>
	</c:if>

	<table class="board_list" summary="부서, 부서장, 지정 상태, 지정·해제">
	<caption>부서장 지정 목록</caption>
	<colgroup>
		<col style="width: 6%;">
		<col style="width: 18%;">
		<col style="width: 22%;">
		<col style="width: 16%;">
		<col style="width: 38%;">
	</colgroup>
	<thead>
	<tr>
		<th scope="col">번호</th>
		<th scope="col">부서</th>
		<th scope="col">부서장</th>
		<th scope="col">상태</th>
		<th scope="col">지정·해제</th>
	</tr>
	</thead>
	<tbody class="ov">
	<c:if test="${fn:length(resultList) == 0}">
	<tr>
		<td colspan="5">부서가 없습니다.</td>
	</tr>
	</c:if>
	<c:forEach items="${resultList}" var="dept" varStatus="status">
	<tr>
		<td><c:out value="${(searchVO.pageIndex - 1) * searchVO.pageUnit + status.count}"/></td>
		<td><c:out value="${dept.orgnztNm}"/></td>
		<td>
			<c:choose>
				<c:when test="${empty dept.emplyrId}">-</c:when>
				<c:otherwise>
					<c:out value="${empty dept.userNm ? '(계정 없음)' : dept.userNm}"/> (<c:out value="${dept.emplyrId}"/>)
				</c:otherwise>
			</c:choose>
		</td>
		<td>
			<c:choose>
				<c:when test="${empty dept.headStatus}">지정 없음(관리자 승인)</c:when>
				<c:when test="${dept.valid}">유효</c:when>
				<c:otherwise>
					<span class="badge_invalid">무효</span>
					<c:choose>
						<c:when test="${dept.headStatus == 'MOVED'}">부서 이동</c:when>
						<c:when test="${dept.headStatus == 'LOCKED'}">잠김</c:when>
						<c:when test="${dept.headStatus == 'DISABLED'}">비활성</c:when>
						<c:when test="${dept.headStatus == 'NO_ROLE'}">권한 없음</c:when>
						<c:when test="${dept.headStatus == 'NO_ACCOUNT'}">계정 없음</c:when>
					</c:choose>
				</c:otherwise>
			</c:choose>
		</td>
		<td class="left">
			<c:set var="deptCandidates" value="${candidates[dept.orgnztId]}"/>
			<c:choose>
				<c:when test="${empty deptCandidates}">지정할 수 있는 직원이 없습니다.</c:when>
				<c:otherwise>
					<form method="post" action="<c:url value='/asset/depthead/assign.do'/>" style="display:inline;">
						<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
						<input type="hidden" name="orgnztId" value="<c:out value='${dept.orgnztId}'/>"/>
						<input type="hidden" name="pageIndex" value="<c:out value='${searchVO.pageIndex}'/>"/>
						<label for="emplyrId_${status.index}" class="blind"><c:out value="${dept.orgnztNm}"/> 부서장 선택</label>
						<select id="emplyrId_${status.index}" name="emplyrId" title="<c:out value='${dept.orgnztNm}'/> 부서장 선택">
							<c:forEach items="${deptCandidates}" var="cand">
								<option value="<c:out value='${cand.emplyrId}'/>" <c:if test="${cand.emplyrId == dept.emplyrId}">selected="selected"</c:if>><c:out value="${cand.userNm}"/> (<c:out value="${cand.emplyrId}"/>)</option>
							</c:forEach>
						</select>
						<input type="submit" class="btn_style3c" value="지정" title="<c:out value='${dept.orgnztNm}'/> 부서장 지정"/>
					</form>
				</c:otherwise>
			</c:choose>
			<c:if test="${not empty dept.emplyrId}">
				<form method="post" action="<c:url value='/asset/depthead/unassign.do'/>" style="display:inline;"
					onsubmit="return confirm('부서장 지정을 해제할까요?');">
					<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
					<input type="hidden" name="orgnztId" value="<c:out value='${dept.orgnztId}'/>"/>
					<input type="hidden" name="pageIndex" value="<c:out value='${searchVO.pageIndex}'/>"/>
					<input type="submit" class="btn_style3c" value="해제" title="<c:out value='${dept.orgnztNm}'/> 부서장 해제"/>
				</form>
			</c:if>
		</td>
	</tr>
	</c:forEach>
	</tbody>
	</table>

	<div class="pagination">
		<ul>
		<ui:pagination paginationInfo="${paginationInfo}" type="image" jsFunction="fn_egov_select_linkPage"/>
		</ul>
	</div>
</div>
</body>
</html>
